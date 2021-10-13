/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.cli;

import joptsimple.OptionSet;

import joptsimple.OptionSpec;

import org.apache.lucene.util.SetOnce;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.KeyStoreAwareCommand;
import org.elasticsearch.cli.SuppressForbidden;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.network.NetworkAddress;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.network.NetworkUtils;
import org.elasticsearch.common.settings.KeyStoreWrapper;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.CheckedConsumer;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.env.Environment;
import org.elasticsearch.http.HttpTransportSettings;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.ssl.CertParsingUtils;
import org.elasticsearch.xpack.core.security.EnrollmentToken;
import org.elasticsearch.xpack.core.security.CommandLineHttpClient;
import org.elasticsearch.xpack.core.security.HttpResponse;

import javax.security.auth.x500.X500Principal;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.elasticsearch.common.ssl.PemUtils.parsePKCS8PemString;
import static org.elasticsearch.discovery.SettingsBasedSeedHostsProvider.DISCOVERY_SEED_HOSTS_SETTING;
import static org.elasticsearch.xpack.core.security.CommandLineHttpClient.createURL;

/**
 * Configures a node to join an existing cluster with security features enabled.
 */
public class EnrollNodeToCluster extends KeyStoreAwareCommand {

    private final OptionSpec<String> enrollmentTokenParam = parser.accepts("enrollment-token", "The enrollment token to use")
        .withRequiredArg()
        .required();
    private final BiFunction<Environment, String, CommandLineHttpClient> clientFunction;

    static final String TLS_CONFIG_DIR_NAME_PREFIX = "tls_auto_config_node_";
    static final String HTTP_AUTOGENERATED_KEYSTORE_NAME = "http_keystore_local_node";
    static final String HTTP_AUTOGENERATED_CA_NAME = "http_ca";
    static final String TRANSPORT_AUTOGENERATED_KEYSTORE_NAME = "transport_keystore_all_nodes";
    static final String TRANSPORT_AUTOGENERATED_KEY_ALIAS = "transport_all_nodes_key";
    static final String TRANSPORT_AUTOGENERATED_CERT_ALIAS = "transport_all_nodes_cert";
    private static final int HTTP_CERTIFICATE_DAYS = 2 * 365;
    private static final int HTTP_KEY_SIZE = 4096;

    public EnrollNodeToCluster(BiFunction<Environment, String, CommandLineHttpClient> clientFunction) {
        super("Configures security so that this node can join an existing cluster");
        this.clientFunction = clientFunction;
    }

    public EnrollNodeToCluster() {
        this(CommandLineHttpClient::new);
    }

    public static void main(String[] args) throws Exception {
        exit(new EnrollNodeToCluster().main(args, Terminal.DEFAULT));
    }

    @Override
    protected void execute(Terminal terminal, OptionSet options, Environment env) throws Exception {

        for (Path dataPath : env.dataFiles()) {
            // TODO: Files.list leaks a file handle because the stream is not closed
            // this effectively doesn't matter since enroll is run in a separate, short lived, process
            // but it should be fixed...
            if (Files.isDirectory(dataPath) && Files.list(dataPath).findAny().isPresent()) {
                throw new UserException(
                    ExitCodes.CONFIG,
                    "Aborting enrolling to cluster. It appears that this is not the first time this node starts."
                );
            }
        }

        final Path ymlPath = env.configFile().resolve("elasticsearch.yml");
        final Path keystorePath = KeyStoreWrapper.keystorePath(env.configFile());
        if (false == Files.exists(ymlPath)
            || false == Files.isRegularFile(ymlPath, LinkOption.NOFOLLOW_LINKS)
            || false == Files.isReadable(ymlPath)) {
            throw new UserException(
                ExitCodes.CONFIG,
                String.format(
                    Locale.ROOT,
                    "Aborting enrolling to cluster. The configuration file [%s] is not a readable regular file",
                    ymlPath
                )
            );
        }

        if (Files.exists(keystorePath)
            && (false == Files.isRegularFile(keystorePath, LinkOption.NOFOLLOW_LINKS) || false == Files.isReadable(keystorePath))) {
            throw new UserException(
                ExitCodes.CONFIG,
                String.format(Locale.ROOT, "Aborting enrolling to cluster. The keystore [%s] is not a readable regular file", ymlPath)
            );
        }

        checkExistingConfiguration(env.settings());

        final ZonedDateTime autoConfigDate = ZonedDateTime.now(ZoneOffset.UTC);
        final String instantAutoConfigName = TLS_CONFIG_DIR_NAME_PREFIX + autoConfigDate.toInstant().getEpochSecond();
        final Path instantAutoConfigDir = env.configFile().resolve(instantAutoConfigName);
        try {
            // it is useful to pre-create the sub-config dir in order to check that the config dir is writable and that file owners match
            Files.createDirectory(instantAutoConfigDir);
            // set permissions to 750, don't rely on umask, we assume auto configuration preserves ownership so we don't have to
            // grant "group" or "other" permissions
            PosixFileAttributeView view = Files.getFileAttributeView(instantAutoConfigDir, PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(PosixFilePermissions.fromString("rwxr-x---"));
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(instantAutoConfigDir);
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw new UserException(
                ExitCodes.CANT_CREATE,
                "Aborting enrolling to cluster. Could not create auto configuration directory",
                e
            );
        }

        final UserPrincipal newFileOwner = Files.getOwner(instantAutoConfigDir, LinkOption.NOFOLLOW_LINKS);
        if (false == newFileOwner.equals(Files.getOwner(env.configFile(), LinkOption.NOFOLLOW_LINKS))) {
            Files.deleteIfExists(instantAutoConfigDir);
            throw new UserException(ExitCodes.CONFIG, "Aborting enrolling to cluster. config dir ownership mismatch");
        }

        final EnrollmentToken enrollmentToken;
        try {
            enrollmentToken = EnrollmentToken.decodeFromString(enrollmentTokenParam.value(options));
        } catch (Exception e) {
            try {
                Files.deleteIfExists(instantAutoConfigDir);
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw new UserException(ExitCodes.DATA_ERROR, "Aborting enrolling to cluster. Invalid enrollment token", e);
        }

        final CommandLineHttpClient client = clientFunction.apply(env, enrollmentToken.getFingerprint());

        // We don't wait for cluster health here. If the user has a token, it means that at least the first node has started
        // successfully so we expect the cluster to be healthy already. If not, this is a sign of a problem and we should bail.
        HttpResponse enrollResponse = null;
        URL enrollNodeUrl = null;
        for (String address: enrollmentToken.getBoundAddress()) {
            enrollNodeUrl = createURL(new URL("https://" + address), "/_security/enroll/node", "");
            enrollResponse = client.execute("GET",
                enrollNodeUrl,
                new SecureString(enrollmentToken.getApiKey().toCharArray()),
                () -> null,
                CommandLineHttpClient::responseBuilder);
            if (enrollResponse.getHttpStatus() == 200 ){
                break;
            }
        }
        if (enrollResponse == null || enrollResponse.getHttpStatus() != 200) {
            Files.deleteIfExists(instantAutoConfigDir);
            throw new UserException(
                ExitCodes.UNAVAILABLE,
                "Aborting enrolling to cluster. " +
                    "Could not communicate with the initial node in any of the addresses from the enrollment token. All of " +
                    enrollmentToken.getBoundAddress() +
                    "where attempted."
            );
        }
        final Map<String, Object> responseMap = enrollResponse.getResponseBody();
        if (responseMap == null) {
            Files.deleteIfExists(instantAutoConfigDir);
            throw new UserException(
                ExitCodes.DATA_ERROR,
                "Aborting enrolling to cluster. Empty response when calling the enroll node API (" + enrollNodeUrl + ")"
            );
        }
        final String httpCaKeyPem = (String) responseMap.get("http_ca_key");
        final String httpCaCertPem = (String) responseMap.get("http_ca_cert");
        final String transportKeyPem = (String) responseMap.get("transport_key");
        final String transportCertPem = (String) responseMap.get("transport_cert");
        @SuppressWarnings("unchecked")
        final List<String> transportAddresses = (List<String>) responseMap.get("nodes_addresses");
        if (Strings.isNullOrEmpty(httpCaCertPem)
            || Strings.isNullOrEmpty(httpCaKeyPem)
            || Strings.isNullOrEmpty(transportKeyPem)
            || Strings.isNullOrEmpty(transportCertPem)
            || null == transportAddresses) {
            Files.deleteIfExists(instantAutoConfigDir);
            throw new UserException(
                ExitCodes.DATA_ERROR,
                "Aborting enrolling to cluster. Invalid response when calling the enroll node API (" + enrollNodeUrl + ")"
            );
        }

        final Tuple<PrivateKey, X509Certificate> httpCa = parseKeyCertFromPem(httpCaKeyPem, httpCaCertPem);
        final PrivateKey httpCaKey = httpCa.v1();
        final X509Certificate httpCaCert = httpCa.v2();
        final Tuple<PrivateKey, X509Certificate> transport = parseKeyCertFromPem(transportKeyPem, transportCertPem);
        final PrivateKey transportKey = transport.v1();
        final X509Certificate transportCert = transport.v2();

        final X500Principal certificatePrincipal = new X500Principal("CN=Autogenerated by Elasticsearch");
        // this does DNS resolve and could block
        final GeneralNames subjectAltNames = getSubjectAltNames();

        final KeyPair nodeHttpKeyPair;
        final X509Certificate nodeHttpCert;

        try {
            nodeHttpKeyPair = CertGenUtils.generateKeyPair(HTTP_KEY_SIZE);
            nodeHttpCert = CertGenUtils.generateSignedCertificate(
                certificatePrincipal,
                subjectAltNames,
                nodeHttpKeyPair,
                httpCaCert,
                httpCaKey,
                false,
                HTTP_CERTIFICATE_DAYS,
                null
            );
        } catch (Exception e) {
            try {
                Files.deleteIfExists(instantAutoConfigDir);
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw new UserException(
                ExitCodes.IO_ERROR,
                "Aborting enrolling to cluster. Failed to generate necessary key and certificate material",
                e
            );
        }

        try {
            fullyWriteFile(instantAutoConfigDir, HTTP_AUTOGENERATED_CA_NAME + ".crt", false, stream -> {
                try (
                    JcaPEMWriter pemWriter =
                        new JcaPEMWriter(new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8)))) {
                    pemWriter.writeObject(httpCaCert);
                }
            });
        } catch (Exception e) {
            try {
                Files.deleteIfExists(instantAutoConfigDir);
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw new UserException(
                ExitCodes.IO_ERROR,
                "Aborting enrolling to cluster. Could not store necessary key and certificates.",
                e
            );
        }

        // save original keystore before updating (replacing)
        final Path keystoreBackupPath = env.configFile()
            .resolve(KeyStoreWrapper.KEYSTORE_FILENAME + "." + autoConfigDate.toInstant().getEpochSecond() + ".orig");
        if (Files.exists(keystorePath)) {
            try {
                Files.copy(keystorePath, keystoreBackupPath, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(instantAutoConfigDir);
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
                throw new UserException(
                    ExitCodes.IO_ERROR,
                    "Aborting enrolling to cluster. Could not create backup of existing keystore file",
                    e
                );
            }
        }

        final SetOnce<SecureString> nodeKeystorePassword = new SetOnce<>();
        try (KeyStoreWrapper nodeKeystore = KeyStoreWrapper.bootstrap(env.configFile(), () -> {
            nodeKeystorePassword.set(new SecureString(terminal.readSecret("", KeyStoreWrapper.MAX_PASSPHRASE_LENGTH)));
            return nodeKeystorePassword.get().clone();
        })) {
            // do not overwrite keystore entries
            // instead expect the user to manually remove them themselves
            if (nodeKeystore.getSettingNames().contains("xpack.security.transport.ssl.keystore.secure_password")
                || nodeKeystore.getSettingNames().contains("xpack.security.transport.ssl.truststore.secure_password")
                || nodeKeystore.getSettingNames().contains("xpack.security.http.ssl.keystore.secure_password")) {
                throw new UserException(
                    ExitCodes.CONFIG,
                    "Aborting enrolling to cluster. The node keystore contains TLS related settings already."
                );
            }
            try (SecureString httpKeystorePassword = newKeystorePassword()) {
                final KeyStore httpKeystore = KeyStore.getInstance("PKCS12");
                httpKeystore.load(null);
                httpKeystore.setKeyEntry(
                    HTTP_AUTOGENERATED_KEYSTORE_NAME + "_ca",
                    httpCaKey,
                    httpKeystorePassword.getChars(),
                    new Certificate[] { httpCaCert }
                );
                httpKeystore.setKeyEntry(
                    HTTP_AUTOGENERATED_KEYSTORE_NAME,
                    nodeHttpKeyPair.getPrivate(),
                    httpKeystorePassword.getChars(),
                    new Certificate[] { nodeHttpCert, httpCaCert }
                );
                fullyWriteFile(
                    instantAutoConfigDir,
                    HTTP_AUTOGENERATED_KEYSTORE_NAME + ".p12",
                    false,
                    stream -> httpKeystore.store(stream, httpKeystorePassword.getChars())
                );
                nodeKeystore.setString("xpack.security.http.ssl.keystore.secure_password", httpKeystorePassword.getChars());
            }

            try (SecureString transportKeystorePassword = newKeystorePassword()) {
                KeyStore transportKeystore = KeyStore.getInstance("PKCS12");
                transportKeystore.load(null);
                // the PKCS12 keystore and the contained private key use the same password
                transportKeystore.setKeyEntry(
                    TRANSPORT_AUTOGENERATED_KEY_ALIAS,
                    transportKey,
                    transportKeystorePassword.getChars(),
                    new Certificate[] { transportCert }
                );
                // the transport keystore is used as a truststore too, hence it must contain a certificate entry
                transportKeystore.setCertificateEntry(TRANSPORT_AUTOGENERATED_CERT_ALIAS, transportCert);
                fullyWriteFile(
                    instantAutoConfigDir,
                    TRANSPORT_AUTOGENERATED_KEYSTORE_NAME + ".p12",
                    false,
                    stream -> transportKeystore.store(stream, transportKeystorePassword.getChars())
                );
                nodeKeystore.setString("xpack.security.transport.ssl.keystore.secure_password", transportKeystorePassword.getChars());
                // we use the same PKCS12 file for the keystore and the truststore
                nodeKeystore.setString("xpack.security.transport.ssl.truststore.secure_password", transportKeystorePassword.getChars());
            }
            // finally overwrites the node keystore (if the keystore have been successfully written)
            nodeKeystore.save(env.configFile(), nodeKeystorePassword.get() == null ? new char[0] : nodeKeystorePassword.get().getChars());
        } catch (Exception e) {
            // restore keystore to revert possible keystore bootstrap
            try {
                if (Files.exists(keystoreBackupPath)) {
                    Files.move(
                        keystoreBackupPath,
                        keystorePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.COPY_ATTRIBUTES
                    );
                } else {
                    Files.deleteIfExists(keystorePath);
                }
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            try {
                Files.deleteIfExists(instantAutoConfigDir);
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw new UserException(
                ExitCodes.IO_ERROR,
                "Aborting enrolling to cluster. Could not store necessary key and certificates.",
                e
            );
        } finally {
            if (nodeKeystorePassword.get() != null) {
                nodeKeystorePassword.get().close();
            }
        }

        // We have everything, let's write to the config
        try {
            List<String> existingConfigLines = Files.readAllLines(ymlPath, StandardCharsets.UTF_8);
            fullyWriteFile(env.configFile(), "elasticsearch.yml", true, stream -> {
                try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                    // start with the existing config lines
                    for (String line : existingConfigLines) {
                        bw.write(line);
                        bw.newLine();
                    }
                    bw.newLine();
                    bw.newLine();
                    bw.write("###################################################################################");
                    bw.newLine();
                    bw.write("# The following settings, and associated TLS certificates and keys configuration, #");
                    bw.newLine();
                    bw.write("# have been automatically generated in order to configure Security.               #");
                    bw.newLine();
                    bw.write("# These have been generated the first time that the new node was started, when    #");
                    bw.newLine();
                    bw.write("# enrolling to an existing cluster                                                #");
                    bw.write(String.format(Locale.ROOT, "# %-79s #", ""));
                    bw.newLine();
                    bw.write(String.format(Locale.ROOT, "# %-79s #", autoConfigDate));
                    // TODO add link to docs
                    bw.newLine();
                    bw.write("###################################################################################");
                    bw.newLine();
                    bw.newLine();
                    bw.write(XPackSettings.SECURITY_ENABLED.getKey() + ": true");
                    bw.newLine();
                    bw.newLine();
                    if (false == env.settings().hasValue(XPackSettings.ENROLLMENT_ENABLED.getKey())) {
                        bw.write(XPackSettings.ENROLLMENT_ENABLED.getKey() + ": true");
                        bw.newLine();
                        bw.newLine();
                    }

                    bw.write("xpack.security.transport.ssl.enabled: true");
                    bw.newLine();
                    bw.write("# All the nodes use the same key and certificate on the inter-node connection");
                    bw.newLine();
                    bw.write("xpack.security.transport.ssl.verification_mode: certificate");
                    bw.newLine();
                    bw.write(
                        "xpack.security.transport.ssl.keystore.path: "
                            + instantAutoConfigDir.resolve(TRANSPORT_AUTOGENERATED_KEYSTORE_NAME + ".p12")
                    );
                    bw.newLine();
                    // we use the keystore as a truststore in order to minimize the number of auto-generated resources,
                    // and also because a single file is more idiomatic to the scheme of a shared secret between the cluster nodes
                    // no one should only need the TLS cert without the associated key for the transport layer
                    bw.write(
                        "xpack.security.transport.ssl.truststore.path: "
                            + instantAutoConfigDir.resolve(TRANSPORT_AUTOGENERATED_KEYSTORE_NAME + ".p12")
                    );
                    bw.newLine();

                    bw.newLine();
                    bw.write("xpack.security.http.ssl.enabled: true");
                    bw.newLine();
                    bw.write(
                        "xpack.security.http.ssl.keystore.path: " + instantAutoConfigDir.resolve(HTTP_AUTOGENERATED_KEYSTORE_NAME + ".p12")
                    );
                    bw.newLine();
                    bw.write("# We set seed.hosts so that the node can actually discover the existing nodes in the cluster");
                    bw.newLine();
                    bw.write(
                        DISCOVERY_SEED_HOSTS_SETTING.getKey()
                            + ": ["
                            + transportAddresses.stream().map(p -> '"' + p + '"').collect(Collectors.joining(", "))
                            + "]"
                    );
                    bw.newLine();

                    // if any address settings have been set, assume the admin has thought it through wrt to addresses,
                    // and don't try to be smart and mess with that
                    if (false == (env.settings().hasValue(HttpTransportSettings.SETTING_HTTP_HOST.getKey())
                        || env.settings().hasValue(HttpTransportSettings.SETTING_HTTP_BIND_HOST.getKey())
                        || env.settings().hasValue(HttpTransportSettings.SETTING_HTTP_PUBLISH_HOST.getKey())
                        || env.settings().hasValue(NetworkService.GLOBAL_NETWORK_HOST_SETTING.getKey())
                        || env.settings().hasValue(NetworkService.GLOBAL_NETWORK_BIND_HOST_SETTING.getKey())
                        || env.settings().hasValue(NetworkService.GLOBAL_NETWORK_PUBLISH_HOST_SETTING.getKey()))) {
                        bw.newLine();
                        bw.write(
                            "# With security now configured, which includes user authentication over HTTPs, "
                                + "it's reasonable to serve requests on the local network too"
                        );
                        bw.newLine();
                        bw.write(HttpTransportSettings.SETTING_HTTP_HOST.getKey() + ": [_local_, _site_]");
                        bw.newLine();
                    }
                }
            });
        } catch (Exception e) {
            try {
                if (Files.exists(keystoreBackupPath)) {
                    Files.move(
                        keystoreBackupPath,
                        keystorePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.COPY_ATTRIBUTES
                    );
                } else {
                    Files.deleteIfExists(keystorePath);
                }
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            try {
                Files.deleteIfExists(instantAutoConfigDir);
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw new UserException(
                ExitCodes.IO_ERROR,
                "Aborting enrolling to cluster. Could not persist configuration in elasticsearch.yml",
                e
            );
        }
        // only delete the backed up file if all went well
        Files.deleteIfExists(keystoreBackupPath);

    }

    private static void fullyWriteFile(Path basePath, String fileName, boolean replace, CheckedConsumer<OutputStream, Exception> writer)
        throws Exception {
        boolean success = false;
        Path filePath = basePath.resolve(fileName);
        if (false == replace && Files.exists(filePath)) {
            throw new UserException(
                ExitCodes.IO_ERROR,
                String.format(Locale.ROOT, "Output file [%s] already exists and will not be replaced", filePath)
            );
        }
        // the default permission
        Set<PosixFilePermission> permission = PosixFilePermissions.fromString("rw-rw----");
        // if replacing, use the permission of the replaced file
        if (Files.exists(filePath)) {
            PosixFileAttributeView view = Files.getFileAttributeView(filePath, PosixFileAttributeView.class);
            if (view != null) {
                permission = view.readAttributes().permissions();
            }
        }
        Path tmpPath = basePath.resolve(fileName + "." + UUIDs.randomBase64UUID() + ".tmp");
        try (OutputStream outputStream = Files.newOutputStream(tmpPath, StandardOpenOption.CREATE_NEW)) {
            writer.accept(outputStream);
            PosixFileAttributeView view = Files.getFileAttributeView(tmpPath, PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(permission);
            }
            success = true;
        } finally {
            if (success) {
                if (replace) {
                    if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)
                        && false == Files.getOwner(tmpPath, LinkOption.NOFOLLOW_LINKS)
                            .equals(Files.getOwner(filePath, LinkOption.NOFOLLOW_LINKS))) {
                        Files.deleteIfExists(tmpPath);
                        String message = String.format(
                            Locale.ROOT,
                            "will not overwrite file at [%s], because this incurs changing the file owner",
                            filePath
                        );
                        throw new UserException(ExitCodes.CONFIG, message);
                    }
                    Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } else {
                    Files.move(tmpPath, filePath, StandardCopyOption.ATOMIC_MOVE);
                }
            }
            Files.deleteIfExists(tmpPath);
        }
    }

    SecureString newKeystorePassword() {
        return UUIDs.randomBase64UUIDSecureString();
    }

    @SuppressForbidden(reason = "DNS resolve InetAddress#getCanonicalHostName used to populate auto generated HTTPS cert")
    private GeneralNames getSubjectAltNames() throws IOException {
        Set<GeneralName> generalNameSet = new HashSet<>();
        // use only ipv4 addresses
        // ipv6 can also technically be used, but they are many and they are long
        for (InetAddress ip : NetworkUtils.getAllIPV4Addresses()) {
            String ipString = NetworkAddress.format(ip);
            generalNameSet.add(new GeneralName(GeneralName.iPAddress, ipString));
            String reverseFQDN = ip.getCanonicalHostName();
            if (false == ipString.equals(reverseFQDN)) {
                // reverse FQDN successful
                generalNameSet.add(new GeneralName(GeneralName.dNSName, reverseFQDN));
            }
        }
        return new GeneralNames(generalNameSet.toArray(new GeneralName[0]));
    }

    private Tuple<PrivateKey, X509Certificate> parseKeyCertFromPem(String pemFormattedKey, String pemFormattedCert) throws UserException {
        final PrivateKey key;
        final X509Certificate cert;
        try {
            final List<Certificate> certs = CertParsingUtils.readCertificates(
                Base64.getDecoder().wrap(new ByteArrayInputStream(pemFormattedCert.getBytes(StandardCharsets.UTF_8)))
            );
            if (certs.size() != 1) {
                throw new IllegalStateException("Enroll node API returned multiple certificates");
            }
            cert = (X509Certificate) certs.get(0);
            key = parsePKCS8PemString(pemFormattedKey);
            return new Tuple<>(key, cert);
        } catch (Exception e) {
            throw new UserException(
                ExitCodes.DATA_ERROR,
                "Aborting enrolling to cluster. Failed to parse Private Key and Certificate from the response of the Enroll Node API",
                e
            );
        }
    }

    void checkExistingConfiguration(Settings settings) throws UserException {
        if (XPackSettings.SECURITY_ENABLED.exists(settings)) {
            throw new UserException(ExitCodes.CONFIG, "Aborting enrolling to cluster. It appears that security is already configured.");
        }
        if (XPackSettings.ENROLLMENT_ENABLED.exists(settings) && false == XPackSettings.ENROLLMENT_ENABLED.get(settings)) {
            throw new UserException(ExitCodes.CONFIG, "Aborting enrolling to cluster. Enrollment is explicitly disabled.");
        }
        if (false == settings.getByPrefix(XPackSettings.TRANSPORT_SSL_PREFIX).isEmpty() ||
            false == settings.getByPrefix(XPackSettings.HTTP_SSL_PREFIX).isEmpty()) {
            throw new UserException(ExitCodes.CONFIG, "Aborting enrolling to cluster. It appears that TLS is already configured.");
        }
    }

}
