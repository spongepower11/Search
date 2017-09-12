/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cloud.azure.storage;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.LocationMode;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.RetryExponentialRetry;
import com.microsoft.azure.storage.RetryPolicy;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.BlobProperties;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.Supplier;
import org.elasticsearch.cloud.azure.blobstore.util.SocketAccess;
import org.elasticsearch.common.blobstore.BlobMetaData;
import org.elasticsearch.common.blobstore.support.PlainBlobMetaData;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.repositories.RepositoryException;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class AzureStorageServiceImpl extends AbstractComponent implements AzureStorageService {

    final Map<String, AzureStorageSettings> storageSettings;

    final Map<String, CloudBlobClient> clients = new HashMap<>();

    public AzureStorageServiceImpl(Settings settings, Map<String, AzureStorageSettings> storageSettings) {
        super(settings);

        this.storageSettings = storageSettings;

        if (storageSettings.isEmpty()) {
            // If someone did not register any settings, they basically can't use the plugin
            throw new IllegalArgumentException("If you want to use an azure repository, you need to define a client configuration.");
        }

        logger.debug("starting azure storage client instance");

        registerProxy();

        // We register all regular azure clients
        for (Map.Entry<String, AzureStorageSettings> azureStorageSettingsEntry : this.storageSettings.entrySet()) {
            logger.debug("registering regular client for account [{}]", azureStorageSettingsEntry.getKey());
            createClient(azureStorageSettingsEntry.getValue());
        }
    }

    private void registerProxy() {
        // Register the proxy if we have any
        Proxy.Type proxyType = Storage.PROXY_TYPE_SETTING.get(settings);
        String proxyHost = Storage.PROXY_HOST_SETTING.get(settings);
        Integer proxyPort = Storage.PROXY_PORT_SETTING.get(settings);

        // Validate proxy settings
        if (proxyType.equals(Proxy.Type.DIRECT) && (proxyPort != 0 || Strings.hasText(proxyHost))) {
            throw new SettingsException("Azure Proxy port or host have been set but proxy type is not defined.");
        }
        if (proxyType.equals(Proxy.Type.DIRECT) == false && (proxyPort == 0 || Strings.isEmpty(proxyHost))) {
            throw new SettingsException("Azure Proxy type has been set but proxy host or port is not defined.");
        }

        if (proxyType.equals(Proxy.Type.DIRECT)) {
            OperationContext.setDefaultProxy(null);
        } else {
            logger.debug("Using azure proxy [{}] with [{}:{}].", proxyType, proxyHost, proxyPort);
            try {
                OperationContext.setDefaultProxy(new Proxy(proxyType, new InetSocketAddress(InetAddress.getByName(proxyHost), proxyPort)));
            } catch (UnknownHostException e) {
                throw new SettingsException("Azure proxy host is unknown.", e);
            }
        }
    }

    void createClient(AzureStorageSettings azureStorageSettings) {
        try {
            logger.trace("creating new Azure storage client using account [{}], key [{}]",
                azureStorageSettings.getAccount(), azureStorageSettings.getKey());

            String storageConnectionString =
                "DefaultEndpointsProtocol=https;"
                    + "AccountName=" + azureStorageSettings.getAccount() + ";"
                    + "AccountKey=" + azureStorageSettings.getKey();

            // Retrieve storage account from connection-string.
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);

            // Create the blob client.
            CloudBlobClient client = storageAccount.createCloudBlobClient();

            // Register the client
            this.clients.put(azureStorageSettings.getAccount(), client);
        } catch (Exception e) {
            logger.error("can not create azure storage client: {}", e.getMessage());
        }
    }

    CloudBlobClient getSelectedClient(String clientName, LocationMode mode) {
        logger.trace("selecting a client named [{}], mode [{}]", clientName, mode.name());
        AzureStorageSettings azureStorageSettings = this.storageSettings.get(clientName);
        if (azureStorageSettings == null) {
            throw new IllegalArgumentException("Can not find named azure client [" + clientName + "]. Check your settings.");
        }

        CloudBlobClient client = this.clients.get(azureStorageSettings.getAccount());

        if (client == null) {
            throw new IllegalArgumentException("Can not find an azure client named [" + azureStorageSettings.getAccount() + "]");
        }

        // NOTE: for now, just set the location mode in case it is different;
        // only one mode per storage clientName can be active at a time
        client.getDefaultRequestOptions().setLocationMode(mode);

        // Set timeout option if the user sets cloud.azure.storage.timeout or cloud.azure.storage.xxx.timeout (it's negative by default)
        if (azureStorageSettings.getTimeout().getSeconds() > 0) {
            try {
                int timeout = (int) azureStorageSettings.getTimeout().getMillis();
                client.getDefaultRequestOptions().setTimeoutIntervalInMs(timeout);
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Can not convert [" + azureStorageSettings.getTimeout() +
                    "]. It can not be longer than 2,147,483,647ms.");
            }
        }

        // We define a default exponential retry policy
        client.getDefaultRequestOptions().setRetryPolicyFactory(
            new RetryExponentialRetry(RetryPolicy.DEFAULT_CLIENT_BACKOFF, azureStorageSettings.getMaxRetries()));

        return client;
    }

    @Override
    public boolean doesContainerExist(String account, LocationMode mode, String container) {
        try {
            CloudBlobClient client = this.getSelectedClient(account, mode);
            CloudBlobContainer blobContainer = client.getContainerReference(container);
            return SocketAccess.doPrivilegedException(blobContainer::exists);
        } catch (Exception e) {
            logger.error("can not access container [{}]", container);
        }
        return false;
    }

    @Override
    public void removeContainer(String account, LocationMode mode, String container) throws URISyntaxException, StorageException {
        CloudBlobClient client = this.getSelectedClient(account, mode);
        CloudBlobContainer blobContainer = client.getContainerReference(container);
        logger.trace("removing container [{}]", container);
        SocketAccess.doPrivilegedException(blobContainer::deleteIfExists);
    }

    @Override
    public void createContainer(String account, LocationMode mode, String container) throws URISyntaxException, StorageException {
        try {
            CloudBlobClient client = this.getSelectedClient(account, mode);
            CloudBlobContainer blobContainer = client.getContainerReference(container);
            logger.trace("creating container [{}]", container);
            SocketAccess.doPrivilegedException(blobContainer::createIfNotExists);
        } catch (IllegalArgumentException e) {
            logger.trace((Supplier<?>) () -> new ParameterizedMessage("fails creating container [{}]", container), e);
            throw new RepositoryException(container, e.getMessage(), e);
        }
    }

    @Override
    public void deleteFiles(String account, LocationMode mode, String container, String path) throws URISyntaxException, StorageException {
        logger.trace("delete files container [{}], path [{}]", container, path);

        // Container name must be lower case.
        CloudBlobClient client = this.getSelectedClient(account, mode);
        CloudBlobContainer blobContainer = client.getContainerReference(container);
        SocketAccess.doPrivilegedVoidException(() -> {
            if (blobContainer.exists()) {
                // We list the blobs using a flat blob listing mode
                for (ListBlobItem blobItem : blobContainer.listBlobs(path, true)) {
                    String blobName = blobNameFromUri(blobItem.getUri());
                    logger.trace("removing blob [{}] full URI was [{}]", blobName, blobItem.getUri());
                    deleteBlob(account, mode, container, blobName);
                }
            }
        });
    }

    /**
     * Extract the blob name from a URI like https://myservice.azure.net/container/path/to/myfile
     * It should remove the container part (first part of the path) and gives path/to/myfile
     * @param uri URI to parse
     * @return The blob name relative to the container
     */
    public static String blobNameFromUri(URI uri) {
        String path = uri.getPath();

        // We remove the container name from the path
        // The 3 magic number cames from the fact if path is /container/path/to/myfile
        // First occurrence is empty "/"
        // Second occurrence is "container
        // Last part contains "path/to/myfile" which is what we want to get
        String[] splits = path.split("/", 3);

        // We return the remaining end of the string
        return splits[2];
    }

    @Override
    public boolean blobExists(String account, LocationMode mode, String container, String blob) throws URISyntaxException, StorageException {
        // Container name must be lower case.
        CloudBlobClient client = this.getSelectedClient(account, mode);
        CloudBlobContainer blobContainer = client.getContainerReference(container);
        if (SocketAccess.doPrivilegedException(blobContainer::exists)) {
            CloudBlockBlob azureBlob = blobContainer.getBlockBlobReference(blob);
            return SocketAccess.doPrivilegedException(azureBlob::exists);
        }

        return false;
    }

    @Override
    public void deleteBlob(String account, LocationMode mode, String container, String blob) throws URISyntaxException, StorageException {
        logger.trace("delete blob for container [{}], blob [{}]", container, blob);

        // Container name must be lower case.
        CloudBlobClient client = this.getSelectedClient(account, mode);
        CloudBlobContainer blobContainer = client.getContainerReference(container);
        if (SocketAccess.doPrivilegedException(blobContainer::exists)) {
            logger.trace("container [{}]: blob [{}] found. removing.", container, blob);
            CloudBlockBlob azureBlob = blobContainer.getBlockBlobReference(blob);
            SocketAccess.doPrivilegedVoidException(azureBlob::delete);
        }
    }

    @Override
    public InputStream getInputStream(String account, LocationMode mode, String container, String blob) throws URISyntaxException, StorageException {
        logger.trace("reading container [{}], blob [{}]", container, blob);
        CloudBlobClient client = this.getSelectedClient(account, mode);
        CloudBlockBlob blockBlobReference = client.getContainerReference(container).getBlockBlobReference(blob);
        return SocketAccess.doPrivilegedException(blockBlobReference::openInputStream);
    }

    @Override
    public OutputStream getOutputStream(String account, LocationMode mode, String container, String blob) throws URISyntaxException, StorageException {
        logger.trace("writing container [{}], blob [{}]", container, blob);
        CloudBlobClient client = this.getSelectedClient(account, mode);
        CloudBlockBlob blockBlobReference = client.getContainerReference(container).getBlockBlobReference(blob);
        return SocketAccess.doPrivilegedException(blockBlobReference::openOutputStream);
    }

    @Override
    public Map<String, BlobMetaData> listBlobsByPrefix(String account, LocationMode mode, String container, String keyPath, String prefix) throws URISyntaxException, StorageException {
        // NOTE: this should be here: if (prefix == null) prefix = "";
        // however, this is really inefficient since deleteBlobsByPrefix enumerates everything and
        // then does a prefix match on the result; it should just call listBlobsByPrefix with the prefix!

        logger.debug("listing container [{}], keyPath [{}], prefix [{}]", container, keyPath, prefix);
        MapBuilder<String, BlobMetaData> blobsBuilder = MapBuilder.newMapBuilder();
        EnumSet<BlobListingDetails> enumBlobListingDetails = EnumSet.of(BlobListingDetails.METADATA);
        CloudBlobClient client = this.getSelectedClient(account, mode);
        CloudBlobContainer blobContainer = client.getContainerReference(container);
        SocketAccess.doPrivilegedVoidException(() -> {
            if (blobContainer.exists()) {
                for (ListBlobItem blobItem : blobContainer.listBlobs(keyPath + (prefix == null ? "" : prefix), false,
                    enumBlobListingDetails, null, null)) {
                    URI uri = blobItem.getUri();
                    logger.trace("blob url [{}]", uri);

                    // uri.getPath is of the form /container/keyPath.* and we want to strip off the /container/
                    // this requires 1 + container.length() + 1, with each 1 corresponding to one of the /
                    String blobPath = uri.getPath().substring(1 + container.length() + 1);
                    BlobProperties properties = ((CloudBlockBlob) blobItem).getProperties();
                    String name = blobPath.substring(keyPath.length());
                    logger.trace("blob url [{}], name [{}], size [{}]", uri, name, properties.getLength());
                    blobsBuilder.put(name, new PlainBlobMetaData(name, properties.getLength()));
                }
            }
        });
        return blobsBuilder.immutableMap();
    }

    @Override
    public void moveBlob(String account, LocationMode mode, String container, String sourceBlob, String targetBlob) throws URISyntaxException, StorageException {
        logger.debug("moveBlob container [{}], sourceBlob [{}], targetBlob [{}]", container, sourceBlob, targetBlob);

        CloudBlobClient client = this.getSelectedClient(account, mode);
        CloudBlobContainer blobContainer = client.getContainerReference(container);
        CloudBlockBlob blobSource = blobContainer.getBlockBlobReference(sourceBlob);
        if (SocketAccess.doPrivilegedException(blobSource::exists)) {
            CloudBlockBlob blobTarget = blobContainer.getBlockBlobReference(targetBlob);
            SocketAccess.doPrivilegedVoidException(() -> {
                blobTarget.startCopy(blobSource);
                blobSource.delete();
            });
            logger.debug("moveBlob container [{}], sourceBlob [{}], targetBlob [{}] -> done", container, sourceBlob, targetBlob);
        }
    }
}
