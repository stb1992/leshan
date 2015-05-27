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

import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.californium.core.network.CoAPEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.tcp.TCPEndpoint;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.leshan.client.LwM2mClient;
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

    private BindingMode bindingMode;
    private ObjectsInitializer initializer;
    private InetSocketAddress localAddress;
    private InetSocketAddress serverAddress;
    private final Set<SecurityMode> securityModes = new HashSet<SecurityMode>();
    private String pskIdentity;
    private byte[] pskKey;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    /**
     * Set the remote LW-M2M provider the client should connect to.
     * 
     * @param remoteServer The particular LW-M2M provider to connect to it. If none is provided localhost:5683.
     * @return
     */
    public LeshanClientBuilder setServerAddress(InetSocketAddress serverAddress) {
        Validate.notNull(serverAddress);

        this.serverAddress = serverAddress;

        return this;
    }

    /**
     * Add PSK authentication to the LW-M2M server.
     * 
     * @param pskIdentity the PSK Identity to use.
     * @param pskKey the PSK key to use.
     * @return
     */
    public LeshanClientBuilder setPskSecurity(String pskIdentity, byte[] pskKey) {
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
    public LeshanClientBuilder setRpkSecurity(PrivateKey clientPrivateKey, PublicKey clientPublicKey) {
        this.securityModes.add(SecurityMode.RPK);
        this.privateKey = clientPrivateKey;
        this.publicKey = clientPublicKey;

        return this;
    }

    /**
     * Set the binding mode which the client should use to connect to the LW-M2M provider.
     * 
     * @param bindingMode The particular binding mode as defined in the LW-M2M specification, section 5.2.1.1. If none
     *        is provided 'U' (UDP, no Queuing Mode, no SMS) shall be used.
     * @return
     */
    public LeshanClientBuilder setBindingMode(BindingMode bindingMode) {
        Validate.notNull(bindingMode);
        this.bindingMode = bindingMode;

        return this;
    }

    /**
     * Set the initializer used by the client to create objects, object instances and resources for the LW-M2M provider.
     * 
     * @param initializer The particular initializer. If none is provided, a default initializer with default settings
     *        derived from the OMA LW-M2M JSON schema will be used.
     * @return
     */
    public LeshanClientBuilder setObjectsInitializer(ObjectsInitializer initializer) {
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
    public LeshanClientBuilder setLocalAddress(InetSocketAddress localAddress) {
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
        if (bindingMode == null)
            bindingMode = BindingMode.U;
        if (initializer == null)
            initializer = new ObjectsInitializer();
        if (securityModes.isEmpty())
            securityModes.add(SecurityMode.NO_SEC);
        if (objectId == null)
            objectId = new int[] {};

        List<ObjectEnabler> objects = objectId.length == 0 ? initializer.createMandatory() : initializer
                .create(objectId);

        Endpoint endpoint;

        switch (bindingMode) {
        case T:
            endpoint = TCPEndpoint.getNewTcpEndpointBuilder().setAsTcpClient()
                    .setRemoteAddress(serverAddress.getHostName()).setPort(serverAddress.getPort()).buildTcpEndpoint();
            break;
        case U:
            if (securityModes.contains(SecurityMode.NO_SEC)) {
                endpoint = new CoAPEndpoint(localAddress);
            } else {
                DTLSConnector dtlsConnector = new DTLSConnector(localAddress);

                if (securityModes.contains(SecurityMode.PSK)) {
                    // TODO The preferred CipherSuite should not be necessary, if I only set the PSK (scandium bug ?)
                    dtlsConnector.getConfig().setPreferredCipherSuite(CipherSuite.TLS_PSK_WITH_AES_128_CCM_8);
                    dtlsConnector.getConfig().setPskStore(new StaticPskStore(pskIdentity, pskKey));
                }
                if (securityModes.contains(SecurityMode.RPK)) {
                    // TODO The preferred CipherSuite should not be necessary, if I only set the PSK (scandium bug ?)
                    dtlsConnector.getConfig().setPreferredCipherSuite(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8);
                    dtlsConnector.getConfig().setPrivateKey(privateKey, publicKey);
                }

                endpoint = new CoAPEndpoint(dtlsConnector, NetworkConfig.getStandard());
            }
            break;
        default:
            throw new IllegalArgumentException("Leshan Client does not currently support the selected BindingMode "
                    + bindingMode);
        }

        return new LeshanClient(endpoint, serverAddress, new ArrayList<LwM2mObjectEnabler>(objects));
    }

}
