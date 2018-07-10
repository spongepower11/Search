/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.transport.nio;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.recycler.Recycler;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.PageCacheRecycler;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.nio.BytesChannelContext;
import org.elasticsearch.nio.InboundChannelBuffer;
import org.elasticsearch.nio.NioSelector;
import org.elasticsearch.nio.NioSocketChannel;
import org.elasticsearch.nio.ServerChannelContext;
import org.elasticsearch.nio.SocketChannelContext;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TcpTransport;
import org.elasticsearch.transport.nio.NioTcpChannel;
import org.elasticsearch.transport.nio.NioTcpServerChannel;
import org.elasticsearch.transport.nio.NioTransport;
import org.elasticsearch.transport.nio.TcpReadWriteHandler;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.security.transport.netty4.SecurityNetty4Transport;
import org.elasticsearch.xpack.core.ssl.SSLConfiguration;
import org.elasticsearch.xpack.core.ssl.SSLService;
import org.elasticsearch.xpack.security.transport.filter.IPFilter;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.core.security.SecurityField.setting;

/**
 * This transport provides a transport based on nio that is secured by SSL/TLS. SSL/TLS is a communications
 * protocol that allows two channels to go through a handshake process prior to application data being
 * exchanged. The handshake process enables the channels to exchange parameters that will allow them to
 * encrypt the application data they exchange.
 * <p>
 * The specific SSL/TLS parameters and configurations are setup in the {@link SSLService} class. The actual
 * implementation of the SSL/TLS layer is in the {@link SSLChannelContext} and {@link SSLDriver} classes.
 */
public class SecurityNioTransport extends NioTransport {

    private final IPFilter authenticator;
    private final SSLService sslService;
    private final Map<String, SSLConfiguration> profileConfiguration;
    private final boolean sslEnabled;

    public SecurityNioTransport(Settings settings, ThreadPool threadPool, NetworkService networkService, BigArrays bigArrays,
                                PageCacheRecycler pageCacheRecycler, NamedWriteableRegistry namedWriteableRegistry,
                                CircuitBreakerService circuitBreakerService, @Nullable final IPFilter authenticator,
                                SSLService sslService) {
        super(settings, threadPool, networkService, bigArrays, pageCacheRecycler, namedWriteableRegistry, circuitBreakerService);
        this.authenticator = authenticator;
        this.sslService = sslService;
        this.sslEnabled = XPackSettings.TRANSPORT_SSL_ENABLED.get(settings);
        final Settings transportSSLSettings = settings.getByPrefix(setting("transport.ssl."));
        if (sslEnabled) {
            Map<String, Settings> profileSettingsMap = settings.getGroups("transport.profiles.", true);
            Map<String, SSLConfiguration> profileConfiguration = new HashMap<>(profileSettingsMap.size() + 1);
            for (Map.Entry<String, Settings> entry : profileSettingsMap.entrySet()) {
                Settings profileSettings = entry.getValue();
                final Settings profileSslSettings = SecurityNetty4Transport.profileSslSettings(profileSettings);
                SSLConfiguration configuration = sslService.sslConfiguration(profileSslSettings, transportSSLSettings);
                profileConfiguration.put(entry.getKey(), configuration);
            }

            if (profileConfiguration.containsKey(TcpTransport.DEFAULT_PROFILE) == false) {
                profileConfiguration.put(TcpTransport.DEFAULT_PROFILE, sslService.sslConfiguration(transportSSLSettings, Settings.EMPTY));
            }

            this.profileConfiguration = Collections.unmodifiableMap(profileConfiguration);
        } else {
            profileConfiguration = Collections.emptyMap();
        }
    }

    @Override
    protected void doStart() {
        super.doStart();
        if (authenticator != null) {
            authenticator.setBoundTransportAddress(boundAddress(), profileBoundAddresses());
        }
    }

    @Override
    protected TcpChannelFactory channelFactory(ProfileSettings profileSettings, boolean isClient) {
        return new SecurityTcpChannelFactory(profileSettings, isClient);
    }

    private boolean validateChannel(NioSocketChannel channel) {
        if (authenticator != null) {
            NioTcpChannel nioTcpChannel = (NioTcpChannel) channel;
            return authenticator.accept(nioTcpChannel.getProfile(), nioTcpChannel.getRemoteAddress());
        } else {
            return true;
        }
    }

    private class SecurityTcpChannelFactory extends TcpChannelFactory {

        private final String profileName;
        private final boolean isClient;

        private SecurityTcpChannelFactory(ProfileSettings profileSettings, boolean isClient) {
            super(new RawChannelFactory(profileSettings.tcpNoDelay,
                profileSettings.tcpKeepAlive,
                profileSettings.reuseAddress,
                Math.toIntExact(profileSettings.sendBufferSize.getBytes()),
                Math.toIntExact(profileSettings.receiveBufferSize.getBytes())));
            this.profileName = profileSettings.profileName;
            this.isClient = isClient;
        }

        @Override
        public NioTcpChannel createChannel(NioSelector selector, SocketChannel channel) throws IOException {
            NioTcpChannel nioChannel = new NioTcpChannel(profileName, channel);
            SocketChannelContext context;
            Supplier<InboundChannelBuffer.Page> pageSupplier = () -> {
                Recycler.V<byte[]> bytes = pageCacheRecycler.bytePage(false);
                return new InboundChannelBuffer.Page(ByteBuffer.wrap(bytes.v()), bytes::close);
            };
            TcpReadWriteHandler readWriteHandler = new TcpReadWriteHandler(nioChannel, SecurityNioTransport.this);
            InboundChannelBuffer buffer = new InboundChannelBuffer(pageSupplier);
            Consumer<Exception> exceptionHandler = (e) -> onException(nioChannel, e);
            Predicate<NioSocketChannel> filter = SecurityNioTransport.this::validateChannel;

            if (sslEnabled) {
                SSLEngine sslEngine;
                SSLConfiguration defaultConfig = profileConfiguration.get(TcpTransport.DEFAULT_PROFILE);
                SSLConfiguration sslConfig = profileConfiguration.getOrDefault(profileName, defaultConfig);
                boolean hostnameVerificationEnabled = sslConfig.verificationMode().isHostnameVerificationEnabled();
                if (hostnameVerificationEnabled) {
                    InetSocketAddress inetSocketAddress = (InetSocketAddress) channel.getRemoteAddress();
                    // we create the socket based on the name given. don't reverse DNS
                    sslEngine = sslService.createSSLEngine(sslConfig, inetSocketAddress.getHostString(), inetSocketAddress.getPort());
                } else {
                    sslEngine = sslService.createSSLEngine(sslConfig, null, -1);
                }
                SSLDriver sslDriver = new SSLDriver(sslEngine, isClient);
                context = new SSLChannelContext(nioChannel, selector, exceptionHandler, sslDriver, readWriteHandler, buffer, filter);
            } else {
                context = new BytesChannelContext(nioChannel, selector, exceptionHandler, readWriteHandler, buffer, filter);
            }
            nioChannel.setContext(context);

            return nioChannel;
        }

        @Override
        public NioTcpServerChannel createServerChannel(NioSelector selector, ServerSocketChannel channel) throws IOException {
            NioTcpServerChannel nioChannel = new NioTcpServerChannel(profileName, channel);
            Consumer<Exception> exceptionHandler = (e) -> onServerException(nioChannel, e);
            Consumer<NioSocketChannel> acceptor = SecurityNioTransport.this::acceptChannel;
            ServerChannelContext context = new ServerChannelContext(nioChannel, this, selector, acceptor, exceptionHandler);
            nioChannel.setContext(context);
            return nioChannel;
        }
    }
}
