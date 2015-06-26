/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Zebra Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.leshan.client.californium;

import io.netty.channel.ChannelOption;

import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.eclipse.californium.core.network.CoAPEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.tcp.TCPEndpoint;
import org.eclipse.californium.elements.config.TCPConnectionConfig;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.leshan.client.LwM2mClient;
import org.eclipse.leshan.client.californium.impl.LwM2mConnectionManager;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.SecurityMode;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.util.Validate;

/**
 * A builder for constructing a Leshan LW-M2M Client paired to a specific LW-M2M Server using a specific set of Security
 * keys for authentication.
 */
public class LeshanClientBuilder {

    /** IANA assigned UDP port for CoAP (so for LWM2M) */
    public static final int PORT = 5683;

    /** IANA assigned UDP port for CoAP with DTLS (so for LWM2M) */
    public static final int PORT_DTLS = 5684;

    /** TCP port for TLS */
    public static final int PORT_TLS = 443;

    private final Set<BindingMode> bindingModes = EnumSet.noneOf(BindingMode.class);
    private ObjectsInitializer initializer;
    private InetSocketAddress localAddress;
    private InetSocketAddress serverAddress;
    private TCPConfigBuilder tcpConfigBuilder;
    private DTLSConfigBuilder dtlsConfigBuilder;



    /**
     * Set the remote LW-M2M provider the client should connect to.
     * 
     * @param remoteServer The particular LW-M2M provider to connect to it. If none is provided localhost:5683.
     * @return
     */
    public LeshanClientBuilder setServerAddress(final InetSocketAddress serverAddress) {
        Validate.notNull(serverAddress);
        this.serverAddress = serverAddress;

        return this;
    }

    public TCPConfigBuilder addBindingModeTCPClient() {
        bindingModes.add(BindingMode.C);
        this.tcpConfigBuilder = new TCPConfigBuilder(this);
        return tcpConfigBuilder;
    }

    public DTLSConfigBuilder addBindingModeUDP() {
        bindingModes.add(BindingMode.U);
        this.dtlsConfigBuilder = new DTLSConfigBuilder(this);
        return dtlsConfigBuilder;
    }

    public LeshanClientBuilder addBindingModeSMS() {
        bindingModes.add(BindingMode.S);
        return this;
    }

    public LeshanClientBuilder addBindingModeQueue() {
        bindingModes.add(BindingMode.Q);
        return this;
    }

    /**
     * Set the initializer used by the client to create objects, object instances and resources for the LW-M2M provider.
     * 
     * @param initializer The particular initializer. If none is provided, a default initializer with default settings
     *        derived from the OMA LW-M2M JSON schema will be used.
     * @return
     */
    public LeshanClientBuilder setObjectsInitializer(final ObjectsInitializer initializer) {
        Validate.notNull(initializer);
        this.initializer = initializer;

        return this;
    }

    /**
     * Set the local address the client expects the LW-M2M provider to communicate back upon.
     * 
     * @param localAddress The particular address. If none is provided, localhost with a randomly assigned port will be
     *        used.
     * @return
     */
    public LeshanClientBuilder setLocalAddress(final InetSocketAddress localAddress) {
        Validate.notNull(localAddress);
        this.localAddress = localAddress;

        return this;
    }

    /**
     * Build a Leshan client.
     * 
     * @param objectId An array of Objects the given ObjectInitializer should create upon starting the leshan client. If
     *        none are provided, the mandatory object instances as specified in the OMA LW-M2M JSON schema will be
     *        created.
     * @return the Leshan client.
     */
    public LwM2mClient build(int... objectId) {
        if (localAddress == null)
            localAddress = new InetSocketAddress("0", 0);
        if (serverAddress == null)
            serverAddress = new InetSocketAddress("0", PORT);
        if (bindingModes.isEmpty())
            bindingModes.add(BindingMode.U);
        if (initializer == null)
            initializer = new ObjectsInitializer();
        if (dtlsConfigBuilder == null) {
            dtlsConfigBuilder = new DTLSConfigBuilder(this);
            dtlsConfigBuilder.noSec();
        }
        if (objectId == null)
            objectId = new int[] {};

        final List<ObjectEnabler> objects = objectId.length == 0 ? initializer.createMandatory() : initializer
                .create(objectId);

        Endpoint endpoint;
        LwM2mConnectionManager lwM2mConnectionManager = null;

        if (bindingModes.contains(BindingMode.Q) || bindingModes.contains(BindingMode.S)
                || bindingModes.contains(BindingMode.T)) {
            throw new IllegalArgumentException(
                    "Leshan Client does not currently support the selected Binding Modes Q, S or T.");
        }
        if (bindingModes.containsAll(Arrays.asList(BindingMode.C, BindingMode.U))) {
            throw new IllegalArgumentException(
                    "Leshan Client does not currently support two Binding Modes simultaneously.");
        }

        if (bindingModes.contains(BindingMode.C)) {
            final LeshanTCPConnectionConfig config = new LeshanTCPConnectionConfig(serverAddress.getHostName(),
                    serverAddress.getPort(), tcpConfigBuilder.isSharable, tcpConfigBuilder.lwM2mConnectionManager);
            if (tcpConfigBuilder.tlsConfigBuilder != null) {
                config.secure(tcpConfigBuilder.tlsConfigBuilder.sslContext);
            }

            endpoint = new TCPEndpoint(config);
            lwM2mConnectionManager = tcpConfigBuilder.lwM2mConnectionManager;
        } else {
            lwM2mConnectionManager = LwM2mConnectionManager.createNullConnectionManager();
            if (dtlsConfigBuilder.securityModes.contains(SecurityMode.NO_SEC)) {
                endpoint = new CoAPEndpoint(localAddress);
            } else {
                final DTLSConnector dtlsConnector = new DTLSConnector(localAddress);

                if (dtlsConfigBuilder.securityModes.contains(SecurityMode.PSK)) {
                    // TODO The preferred CipherSuite should not be necessary, if I only set the PSK (scandium bug
                    // ?)
                    dtlsConnector.getConfig().setPreferredCipherSuite(CipherSuite.TLS_PSK_WITH_AES_128_CCM_8);
                    dtlsConnector.getConfig().setPskStore(
                            new StaticPskStore(dtlsConfigBuilder.pskIdentity, dtlsConfigBuilder.pskKey));
                }
                if (dtlsConfigBuilder.securityModes.contains(SecurityMode.RPK)) {
                    // TODO The preferred CipherSuite should not be necessary, if I only set the PSK (scandium bug
                    // ?)
                    dtlsConnector.getConfig().setPreferredCipherSuite(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8);
                    dtlsConnector.getConfig().setPrivateKey(dtlsConfigBuilder.privateKey, dtlsConfigBuilder.publicKey);
                }

                endpoint = new CoAPEndpoint(dtlsConnector, NetworkConfig.getStandard());
            }
        }

        return new LeshanClient(endpoint, serverAddress, new ArrayList<LwM2mObjectEnabler>(objects),
                lwM2mConnectionManager);
    }

    public class TCPConfigBuilder {
        private boolean isSharable;
        private LwM2mConnectionManager lwM2mConnectionManager;
        private final Map<ChannelOption<?>, Object> options = new HashMap<ChannelOption<?>, Object>();
        private final LeshanClientBuilder clientBuilder;
        private TLSConfigBuilder tlsConfigBuilder;

        private TCPConfigBuilder(final LeshanClientBuilder clientBuilder) {
            this.clientBuilder = clientBuilder;
            this.lwM2mConnectionManager = LwM2mConnectionManager.createNullConnectionManager();
        }


        public TCPConfigBuilder makeConnectionSharable() {
            this.isSharable = true;
            return this;
        }

        public <T> void addChannelOption(final ChannelOption<T> option, final T value) {
            this.options.put(option, value);
        }

        public TLSConfigBuilder secure() {
            this.tlsConfigBuilder = new TLSConfigBuilder(this);
            this.lwM2mConnectionManager = LwM2mConnectionManager.createTLSConnectionManager();

            return tlsConfigBuilder;
        }

        public LeshanClientBuilder configure() {
            return clientBuilder;
        }
    }

    public class TLSConfigBuilder {
        private final TCPConfigBuilder clientBuilder;
        private boolean isSecure = true;
        private SSLContext sslContext;

        private TLSConfigBuilder(final TCPConfigBuilder clientBuilder) {
            this.clientBuilder = clientBuilder;
        }

        public TLSConfigBuilder noSec() {
            isSecure = false;
            return this;
        }

        public TLSConfigBuilder setSSLContext(final SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public TCPConfigBuilder configure() {
            return clientBuilder;
        }
    }

    public class DTLSConfigBuilder {

        private final LeshanClientBuilder clientBuilder;
        private String pskIdentity;
        private byte[] pskKey;
        private PrivateKey privateKey;
        private PublicKey publicKey;
        private final Set<SecurityMode> securityModes = new HashSet<SecurityMode>();
        private final LwM2mConnectionManager lwM2mConnectionManager;

        private DTLSConfigBuilder(final LeshanClientBuilder clientBuilder) {
            this.clientBuilder = clientBuilder;
            this.lwM2mConnectionManager = LwM2mConnectionManager.createNullConnectionManager();
        }

        public DTLSConfigBuilder noSec() {
            securityModes.add(SecurityMode.NO_SEC);
            return this;
        }

        /**
         * Add PSK authentication to the LW-M2M server.
         * 
         * @param pskIdentity the PSK Identity to use.
         * @param pskKey the PSK key to use.
         * @return
         */
        public DTLSConfigBuilder setPskSecurity(final String pskIdentity, final byte[] pskKey) {
            this.securityModes.add(SecurityMode.PSK);
            this.pskIdentity = pskIdentity;
            this.pskKey = pskKey;

            return this;
        }

        /**
         * Add RPK authentication to the LW-M2M server.
         * 
         * @param clientPrivateKey the Private RPK key to use.
         * @param clientPublicKey thePublic RPK key to use.
         * @return
         */
        public DTLSConfigBuilder setRpkSecurity(final PrivateKey clientPrivateKey, final PublicKey clientPublicKey) {
            this.securityModes.add(SecurityMode.RPK);
            this.privateKey = clientPrivateKey;
            this.publicKey = clientPublicKey;

            return this;
        }

        public LeshanClientBuilder configure() {
            if (securityModes.isEmpty()) {
                securityModes.add(SecurityMode.NO_SEC);
            }
            return clientBuilder;
        }
    }

    private class LeshanTCPConnectionConfig extends TCPConnectionConfig {

        private final String remoteAddress;
        private final int remotePort;
        private final boolean makeSharable;
        private final ConnectionStateListener listener;

        private LeshanTCPConnectionConfig(final String remoteAddress, final int remotePort, final boolean makeSharable,
                final ConnectionStateListener listener) {
            super(CommunicationRole.CLIENT);
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;
            this.makeSharable = makeSharable;
            this.listener = listener;
        }

        private void secure(final SSLContext sslContext) {
            setClientSSL(sslContext);
        }

        @Override
        public String getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public int getRemotePort() {
            return remotePort;
        }

        @Override
        public boolean isSharable() {
            return makeSharable;
        }

        @Override
        public ConnectionStateListener getListener() {
            return listener;
        }
    }

}
