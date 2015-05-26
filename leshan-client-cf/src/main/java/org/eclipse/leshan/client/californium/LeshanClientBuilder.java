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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoAPEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.ConnectorBuilder.CommunicationRole;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.leshan.client.LwM2mClient;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.server.Server;
import org.eclipse.leshan.core.model.SecurityMode;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.util.Validate;

/**
 * A builder for constructing a Leshan LW-M2M Client paired to a specific LW-M2M Server.
 */
public class LeshanClientBuilder {

    /** IANA assigned UDP port for CoAP (so for LWM2M) */
    public static final int PORT = 5683;

    /** IANA assigned UDP port for CoAP with DTLS (so for LWM2M) */
    public static final int PORT_DTLS = 5684;

    /** TCP port for TLS */
    public static final int PORT_TLS = 443;

    private Server remoteServer;
    private BindingMode bindingMode;
    private ObjectsInitializer initializer;
    private InetSocketAddress localAddress;

    /**
     * Set the remote LW-M2M provider the client should connect to.
     * 
     * @param remoteServer The particular LW-M2M provider as well as any required authentication to connect to it. If
     *        none is provided localhost:5683 will be used with no-security.
     * @see org.eclipse.leshan.client.server.Server
     * @return
     */
    public LeshanClientBuilder setRemoteServer(Server remoteServer) {
        Validate.notNull(remoteServer);

        if (remoteServer.getSecurityModes().contains(SecurityMode.X509))
            throw new IllegalArgumentException("Leshan Client does not currently support the selected SecurityMode "
                    + remoteServer.getSecurityModes());

        this.remoteServer = remoteServer;

        return this;
    }

    /**
     * Set the binding mode which the client should use to connect to the LW-M2M provider.
     * 
     * @param bindingMode The particular binding mode as defined in the LW-M2M specification, section 5.2.1.1. If none
     *        is provided 'U' shall be used.
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
        if (remoteServer == null)
            remoteServer = Server.createNoSecServer(new InetSocketAddress("0", PORT));
        if (bindingMode == null)
            bindingMode = BindingMode.U;
        if (initializer == null)
            initializer = new ObjectsInitializer();

        List<ObjectEnabler> objects = objectId == null ? initializer.createMandatory() : initializer.create(objectId);

        CommunicationRole communicationRole;

        switch (bindingMode) {
        case T:
            communicationRole = CommunicationRole.CLIENT;
            break;
        case U:
            communicationRole = CommunicationRole.NODE;
            break;
        default:
            throw new IllegalArgumentException("Leshan Client does not currently support the selected BindingMode "
                    + bindingMode);
        }

        CoapServer coapServer = new CoapServer();

        if (!remoteServer.getSecurityModes().contains(SecurityMode.NO_SEC)) {
            DTLSConnector dtlsConnector = new DTLSConnector(localAddress);

            if (remoteServer.getSecurityModes().contains(SecurityMode.PSK)) {
                // TODO The preferred CipherSuite should not be necessary, if I only set the PSK (scandium bug ?)
                dtlsConnector.getConfig().setPreferredCipherSuite(CipherSuite.TLS_PSK_WITH_AES_128_CCM_8);
                dtlsConnector.getConfig().setPskStore(
                        new StaticPskStore(remoteServer.getPskIdentity(), remoteServer.getPskKey()));
            }
            if (remoteServer.getSecurityModes().contains(SecurityMode.RPK)) {
                // TODO The preferred CipherSuite should not be necessary, if I only set the PSK (scandium bug ?)
                dtlsConnector.getConfig().setPreferredCipherSuite(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8);
                dtlsConnector.getConfig().setPrivateKey(remoteServer.getPrivateKey(), remoteServer.getPublicKey());
            }

            coapServer.addEndpoint(new CoAPEndpoint(dtlsConnector, NetworkConfig.getStandard()));
        }

        return new LeshanClient(localAddress, remoteServer.getServerAddress(), coapServer,
                new ArrayList<LwM2mObjectEnabler>(objects), communicationRole);
    }

}
