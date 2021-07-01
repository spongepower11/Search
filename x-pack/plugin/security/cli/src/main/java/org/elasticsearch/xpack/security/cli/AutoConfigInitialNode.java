/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.cli;

import joptsimple.OptionSet;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.elasticsearch.cli.EnvironmentAwareCommand;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.cluster.coordination.ClusterBootstrapService;
import org.elasticsearch.cluster.node.DiscoveryNodeRole;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.network.NetworkUtils;
import org.elasticsearch.common.settings.KeyStoreWrapper;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.http.HttpTransportSettings;
import org.elasticsearch.node.NodeRoleSettings;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.security.authc.RealmSettings;
import org.elasticsearch.xpack.core.security.authc.file.FileRealmSettings;

import javax.security.auth.x500.X500Principal;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.elasticsearch.xpack.security.cli.CertificateTool.fullyWriteFile;

/**
 * Configures a new cluster node, by appending to the elasticsearch.yml, so that it forms a single node cluster with
 * Security enabled. Used to configure only the initial node of a cluster, and only the first time that the node
 * is started. Subsequent nodes can be added to the cluster via the enrollment flow, but this is not used to
 * configure such nodes or to display the necessary configuration (ie the enrollment tokens) for such.
 *
 * This will not run if Security is explicitly configured or if the existing configuration otherwise clashes with the
 * intent of this (i.e. the node is configured so it cannot form a single node cluster).
 */
public class AutoConfigInitialNode extends EnvironmentAwareCommand {

    private static final String TRANSPORT_AUTOGENERATED_KEYSTORE_NAME = "transport_keystore_all_nodes";
    private static final String TRANSPORT_AUTOGENERATED_TRUSTSTORE_NAME = "transport_truststore_all_nodes";
    private static final int TRANSPORT_CERTIFICATE_DAYS = 3 * 365;
    private static final int TRANSPORT_KEY_SIZE = 2048;
    private static final String HTTP_AUTOGENERATED_KEYSTORE_NAME = "http_keystore";
    private static final String HTTP_AUTOGENERATED_TRUSTSTORE_NAME = "http_truststore";
    private static final int HTTP_CA_CERTIFICATE_DAYS = 3 * 365;
    private static final int HTTP_CA_KEY_SIZE = 4096;
    // at least the browser clients are finicky with longer keys and shorter-lived certs lately
    private static final int HTTP_CERTIFICATE_DAYS = 365;
    private static final int HTTP_KEY_SIZE = 4096;

    public AutoConfigInitialNode() {
        super("Generates all the necessary configuration for the initial node of a new secure cluster");
    }

    public static void main(String[] args) throws Exception {
        exit(new AutoConfigInitialNode().main(args, Terminal.DEFAULT));
    }

    @Override
    protected void execute(Terminal terminal, OptionSet options, Environment env) throws Exception {
        if (Files.isDirectory(env.dataFile())) {
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "Skipping security auto configuration because it appears that the node is not starting up for the first time.");
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "The node might already be part of a cluster and this auto setup utility is designed to configure Security for new " +
                            "clusters only.");
            //throw new UserException(ExitCodes.OK, "Skipping security auto configuration: node restarted");
            return;
        }
        if (env.settings().hasValue(XPackSettings.SECURITY_ENABLED.getKey())) {
            // do not try to validate, correct or fill in any incomplete security configuration,
            // but instead rely on the regular node startup to do this validation
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "Skipping security auto configuration because it appears that security is already configured.");
            //throw new UserException(ExitCodes.OK, "Skipping security auto configuration: Security already configured");
            return;
        }
        if (env.settings().hasValue(ClusterBootstrapService.INITIAL_MASTER_NODES_SETTING.getKey())) {
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "Skipping security auto configuration because this node is explicitly configured to form a new cluster.");
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "The node cannot be auto configured to participate in forming a new multi-node secure cluster.");
            //throw new UserException(ExitCodes.OK, "Skipping security auto configuration: configured cluster formation");
            return;
        }
        List<DiscoveryNodeRole> nodeRoles = NodeRoleSettings.NODE_ROLES_SETTING.get(env.settings());
        boolean canBecomeMaster = nodeRoles.contains(DiscoveryNodeRole.MASTER_ROLE) &&
                false == nodeRoles.contains(DiscoveryNodeRole.VOTING_ONLY_NODE_ROLE);
        if (false == canBecomeMaster) {
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "Skipping security auto configuration because the node is configured such that it cannot become master.");
            //throw new UserException(ExitCodes.OK, "Skipping security auto configuration: cannot become master");
            return;
        }
        boolean canHoldSecurityIndex = nodeRoles.stream().anyMatch(DiscoveryNodeRole::canContainData);
        if (false == canHoldSecurityIndex) {
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "Skipping security auto configuration because the node is configured such that it cannot contain data.");
            //throw new UserException(ExitCodes.OK, "Skipping security auto configuration: cannot contain data");
            return;
        }
        if (false == env.settings().getByPrefix(XPackSettings.TRANSPORT_SSL_PREFIX).isEmpty() ||
                false == env.settings().getByPrefix(XPackSettings.HTTP_SSL_PREFIX).isEmpty()) {
            // again, zero validation for the TLS settings, let the node startup do its thing
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "Skipping security auto configuration because it appears that TLS is already configured.");
            //throw new UserException(ExitCodes.OK, "Skipping security auto configuration: TLS already configured");
            return;
        }
        // check that no file realms have been configured.
        // this can technically be improved upon:
        // auto configuration only requires that the file realm be enabled, and, for optimal experience,
        // also be the first in the chain
        if (false == env.settings().getByPrefix(RealmSettings.realmSettingPrefix(FileRealmSettings.TYPE)).isEmpty()) {
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "Skipping security auto configuration because it appears that a file-based realm is already configured.");
            //throw new UserException(ExitCodes.OK, "Skipping security auto configuration: file realm configured");
            return;
        }
        // tolerate enabling enrollment explicitly, as it could be useful to enable it by a command line option
        // only the first time that the node is started
        if (env.settings().hasValue(XPackSettings.ENROLLMENT_ENABLED.getKey()) && false ==
                XPackSettings.ENROLLMENT_ENABLED.get(env.settings())) {
            terminal.println(Terminal.Verbosity.VERBOSE,
                    "Skipping security auto configuration because enrollment is explicitly disabled.");
            //throw new UserException(ExitCodes.OK, "Skipping security auto configuration: enrollment disabled");
            return;
        }

        final ZonedDateTime autoConfigDate = ZonedDateTime.now();
        final String instantAutoConfigName = "auto_generated_" + autoConfigDate.toInstant().getEpochSecond();
        final Path instantAutoConfigDir = env.configFile().resolve(instantAutoConfigName);
        if (false == instantAutoConfigDir.toFile().mkdir()) {
            throw new UserException(ExitCodes.CANT_CREATE, "Could not create auto configuration directory");
        }

        // the transport key-pair is the same across the cluster and is trusted without hostname verification (it is self-signed),
        // do not populate the certificate's IP, DN, and CN certificate fields
        final X500Principal certificatePrincipal = new X500Principal("CN=" + System.getenv("HOSTNAME"));
        Set<GeneralName> generalNameSet = new HashSet<>();
        // use only ipv4 addresses
        // ipv6 can also technically be used, but they are many and they are long
        for (InetAddress ip : NetworkUtils.getAllIPV4Addresses()) {
            String ipString = ip.getHostAddress();
            generalNameSet.add(new GeneralName(GeneralName.iPAddress, ipString));
            String reverseFQDN = ip.getCanonicalHostName();
            if (false == ipString.equals(reverseFQDN)) {
                // reverse FQDN successful
                generalNameSet.add(new GeneralName(GeneralName.dNSName, reverseFQDN));
            }
        }
        final GeneralNames subjectAltNames = new GeneralNames(generalNameSet.toArray(new GeneralName[0]));

        KeyPair transportKeyPair = CertGenUtils.generateKeyPair(TRANSPORT_KEY_SIZE);
        // self-signed which is not a CA
        X509Certificate transportCert = CertGenUtils.generateSignedCertificate(certificatePrincipal,
                subjectAltNames, transportKeyPair, null, null, false, TRANSPORT_CERTIFICATE_DAYS, null);
        KeyPair httpCAKeyPair = CertGenUtils.generateKeyPair(HTTP_CA_KEY_SIZE);
        // self-signed CA
        X509Certificate httpCACert = CertGenUtils.generateSignedCertificate(certificatePrincipal,
                subjectAltNames, httpCAKeyPair, null, null, true, HTTP_CA_CERTIFICATE_DAYS, null);
        KeyPair httpKeyPair = CertGenUtils.generateKeyPair(HTTP_KEY_SIZE);
        // non-CA
        X509Certificate httpCert = CertGenUtils.generateSignedCertificate(certificatePrincipal,
                subjectAltNames, httpKeyPair, httpCACert, httpCAKeyPair.getPrivate(), false, HTTP_CERTIFICATE_DAYS, null);

        try (SecureString nodeKeystorePassword = new SecureString(terminal.readSecret("", KeyStoreWrapper.MAX_PASSPHRASE_LENGTH));
             KeyStoreWrapper nodeKeystore = KeyStoreWrapper.bootstrap(env.configFile(), () -> nodeKeystorePassword)) {
            Path transportKeystoreOutput = instantAutoConfigDir.resolve(TRANSPORT_AUTOGENERATED_KEYSTORE_NAME + ".p12");
            try (SecureString transportKeystorePassword = keystorePassword()) {
                KeyStore transportKeystore = KeyStore.getInstance("PKCS12");
                transportKeystore.load(null);
                // the PKCS12 keystore and the contained private key use the same password
                transportKeystore.setKeyEntry(TRANSPORT_AUTOGENERATED_KEYSTORE_NAME, transportKeyPair.getPrivate(),
                        transportKeystorePassword.getChars(), new Certificate[]{transportCert});
                fullyWriteFile(transportKeystoreOutput, stream -> transportKeystore.store(stream, transportKeystorePassword.getChars()));
                nodeKeystore.setString("xpack.security.transport.ssl.keystore.secure_password", transportKeystorePassword.getChars());
            } finally {
                nodeKeystore.save(env.configFile(), nodeKeystorePassword.getChars());
            }
            Path httpKeystoreOutput = instantAutoConfigDir.resolve(HTTP_AUTOGENERATED_KEYSTORE_NAME + ".p12");
            try (SecureString httpKeystorePassword = keystorePassword()) {
                KeyStore httpKeystore = KeyStore.getInstance("PKCS12");
                httpKeystore.load(null);
                // the keystore contains both the node's and the CA's private keys
                // both keys are encrypted using the same password as the PKCS12 keystore they're contained in
                httpKeystore.setKeyEntry(HTTP_AUTOGENERATED_KEYSTORE_NAME + "_ca", httpCAKeyPair.getPrivate(),
                        httpKeystorePassword.getChars(), new Certificate[]{httpCACert});
                httpKeystore.setKeyEntry(HTTP_AUTOGENERATED_KEYSTORE_NAME, httpKeyPair.getPrivate(),
                        httpKeystorePassword.getChars(), new Certificate[]{httpCert, httpCACert});
                fullyWriteFile(httpKeystoreOutput, stream -> httpKeystore.store(stream, httpKeystorePassword.getChars()));
                nodeKeystore.setString("xpack.security.http.ssl.keystore.secure_password", httpKeystorePassword.getChars());
            } finally {
                nodeKeystore.save(env.configFile(), nodeKeystorePassword.getChars());
            }
        }

        {
            Path transportTruststoreOutput = instantAutoConfigDir.resolve(TRANSPORT_AUTOGENERATED_TRUSTSTORE_NAME + ".p12");
            final KeyStore transportTruststore = KeyStore.getInstance("PKCS12");
            transportTruststore.load(null);
            transportTruststore.setCertificateEntry(TRANSPORT_AUTOGENERATED_TRUSTSTORE_NAME, transportCert);
            fullyWriteFile(transportTruststoreOutput, stream -> transportTruststore.store(stream, new char[0]));
        }

        {
            // the truststore contains only the CA certificate
            // the ES node doesn't strictly require it, but if someone else does, it's good to have it handy
            // so we don't have to share a keystore with a CA key inside it
            Path httpTruststoreOutput = instantAutoConfigDir.resolve(HTTP_AUTOGENERATED_TRUSTSTORE_NAME + ".p12");
            final KeyStore httpTruststore = KeyStore.getInstance("PKCS12");
            httpTruststore.load(null);
            httpTruststore.setCertificateEntry(HTTP_AUTOGENERATED_TRUSTSTORE_NAME + "_ca", httpCACert);
            fullyWriteFile(httpTruststoreOutput, stream -> httpTruststore.store(stream, new char[0]));
        }

        Path path = env.configFile().resolve("elasticsearch.yml");
        FileWriter fw = new FileWriter(path.toFile(), true);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.newLine();
        bw.newLine();
        bw.write("###################################################################################");
        bw.newLine();
        bw.write("# The following settings, and associated TLS certificates and keys configuration, #");
        bw.newLine();
        bw.write("# have been automatically generated in order to configure Security.               #");
        bw.newLine();
        bw.write("# These have been generated the first time that the new node was started without  #");
        bw.newLine();
        bw.write("# joining or enrolling to an existing cluster and only if Security had not been   #");
        bw.newLine();
        bw.write("# explicitly configured beforehand.                                               #");
        bw.newLine();
        bw.write(String.format(Locale.ROOT, "# %-79s #", ""));
        bw.newLine();
        bw.write(String.format(Locale.ROOT, "# %-79s #", autoConfigDate));
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
        int autoRealmOrder = minimumRealmOrder(env.settings());
        bw.write(RealmSettings.ORDER_SETTING.apply(FileRealmSettings.TYPE)
                .getConcreteSettingForNamespace(instantAutoConfigName).getKey() + ": " + autoRealmOrder);
        bw.newLine();

        {
            bw.newLine();
            bw.write("xpack.security.transport.ssl.enabled: true");
            bw.newLine();
            bw.write("# All the nodes use the same key and certificate on the inter-node connection");
            bw.newLine();
            bw.write("xpack.security.transport.ssl.verification_mode: certificate");
            bw.newLine();
            bw.write("xpack.security.transport.ssl.client_authentication: required");
            bw.newLine();
            bw.write("xpack.security.transport.ssl.keystore.path: " + Path.of(instantAutoConfigName,
                    TRANSPORT_AUTOGENERATED_KEYSTORE_NAME + ".p12"));
            bw.newLine();
            bw.write("xpack.security.transport.ssl.truststore.path: " + Path.of(instantAutoConfigName,
                    TRANSPORT_AUTOGENERATED_TRUSTSTORE_NAME + ".p12"));
            bw.newLine();
        }

        {
            bw.newLine();
            bw.write("xpack.security.http.ssl.enabled: true");
            bw.newLine();
            bw.write("xpack.security.http.ssl.keystore.path: " + Path.of(instantAutoConfigName,
                    HTTP_AUTOGENERATED_KEYSTORE_NAME + ".p12"));
            bw.newLine();
            bw.write("# A trustore is set on the HTTP interface in case clients wish to use mTLS");
            bw.newLine();
            bw.write("xpack.security.http.ssl.truststore.path: " + Path.of(instantAutoConfigName,
                    HTTP_AUTOGENERATED_TRUSTSTORE_NAME + ".p12"));
            bw.newLine();
        }

        if (false == env.settings().hasValue(HttpTransportSettings.SETTING_HTTP_HOST.getKey())) {
            bw.newLine();
            bw.write("# With security now configured, it's reasonable to serve requests on the local network too");
            bw.newLine();
            bw.write(HttpTransportSettings.SETTING_HTTP_HOST.getKey() + ": [_local_, _site_]");
        }
        bw.close();
    }

    private Integer minimumRealmOrder(Settings settings) {
        Integer order = 0;
        Settings realmsSettings = settings.getByPrefix(RealmSettings.PREFIX);
        if (realmsSettings == null || realmsSettings.isEmpty()) {
            return order;
        }
        for (String realmType : realmsSettings.names()) {
            // return the first enabled file realm
            Settings realmSettings = realmsSettings.getByPrefix(realmType);
            if (realmSettings == null || realmSettings.isEmpty()) {
                continue;
            }
            for (String realmName : realmSettings.names()) {
                order = Math.min(order,
                        RealmSettings.ORDER_SETTING.apply(realmType).getConcreteSettingForNamespace(realmName).get(settings));
            }
        }
        return order;
    }

    // for tests
    protected SecureString keystorePassword() {
        return UUIDs.randomBase64UUIDSecureString();
    }
}
