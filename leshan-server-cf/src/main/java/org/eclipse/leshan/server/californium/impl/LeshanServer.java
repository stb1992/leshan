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
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.server.californium.impl;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoAPEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.tcp.TcpServerEndpoint;
import org.eclipse.californium.elements.config.ConnectionConfig;
import org.eclipse.californium.elements.config.ConnectionConfig.CommunicationRole;
import org.eclipse.californium.elements.config.TCPConnectionConfig;
import org.eclipse.californium.elements.tcp.server.TcpServerConnector;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.leshan.core.request.DownlinkRequest;
import org.eclipse.leshan.core.response.ExceptionConsumer;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ResponseConsumer;
import org.eclipse.leshan.server.Destroyable;
import org.eclipse.leshan.server.LwM2mServer;
import org.eclipse.leshan.server.Startable;
import org.eclipse.leshan.server.Stoppable;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.client.ClientRegistry;
import org.eclipse.leshan.server.client.ClientRegistryListener;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.observation.ObservationRegistry;
import org.eclipse.leshan.server.registration.RegistrationHandler;
import org.eclipse.leshan.server.security.SecurityRegistry;
import org.eclipse.leshan.util.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Lightweight M2M server.
 * <p>
 * This implementation starts a Californium {@link CoapServer} with a non-secure and secure endpoint. This CoAP server
 * defines a <i>/rd</i> resource as described in the CoRE RD specification.
 * </p>
 * <p>
 * This class is the entry point to send synchronous and asynchronous requests to registered clients.
 * </p>
 * <p>
 * The {@link LeshanServerBuilder} should be the preferred way to build an instance of {@link LeshanServer}.
 * </p>
 */
public class LeshanServer implements LwM2mServer {

    private final CoapServer coapServer;

    private static final Logger LOG = LoggerFactory.getLogger(LeshanServer.class);

    private static final int COAP_REQUEST_TIMEOUT_MILLIS = 5000;

    private final CaliforniumLwM2mRequestSender requestSender;

    private final ClientRegistry clientRegistry;

    private final ObservationRegistry observationRegistry;

    private final SecurityRegistry securityRegistry;

    private final LwM2mModelProvider modelProvider;

    /**
     * Initialize a server which will bind to the specified address and port.
     * TODO: this was modified to accept the Config object, this is pretty ugly code, and needs to be ratified
     *
     * @param localAddress the address to bind the CoAP server.
     * @param localAddressSecure the address to bind the CoAP server for DTLS connection.
     * @param privateKey for RPK authentication mode
     * @param publicKey for RPK authentication mode
     */
    public LeshanServer(final ClientRegistry clientRegistry, final SecurityRegistry securityRegistry,
            		    final ObservationRegistry observationRegistry, final LwM2mModelProvider modelProvider,
            		    final ConnectionConfig config) {

        Validate.notNull(config, "connectionConfig cannot be null");
        Validate.notNull(clientRegistry, "clientRegistry cannot be null");
        Validate.notNull(securityRegistry, "securityRegistry cannot be null");
        Validate.notNull(observationRegistry, "observationRegistry cannot be null");
        Validate.notNull(modelProvider, "modelProvider cannot be null");

        final CommunicationRole role = config.getCommunicationRole();

        // Init registries
        this.clientRegistry = clientRegistry;
        this.securityRegistry = securityRegistry;
        this.observationRegistry = observationRegistry;

        this.modelProvider = modelProvider;

        // Cancel observations on client unregistering
        this.clientRegistry.addListener(new ClientRegistryListener() {

            @Override
            public void updated(final Client clientUpdated) {
            }

            @Override
            public void unregistered(final Client client) {
                LeshanServer.this.observationRegistry.cancelObservations(client);
            }

            @Override
            public void registered(final Client client) {
            }
        });

        // default endpoint
        coapServer = new CoapServer();
        final Set<Endpoint> endpoints = new HashSet<>();

        Endpoint endpoint;
        switch(role) {
        case NODE:
        	endpoint = new CoAPEndpoint(((LeshanUDPConnnectionConfig)config).getLocalAddress());
        	// secure endpoint
            final DTLSConnector connector = new DTLSConnector(((LeshanUDPConnnectionConfig)config).getLocalAddressSecure());
            connector.getConfig().setPskStore(new LwM2mPskStore(this.securityRegistry, this.clientRegistry));
            final PrivateKey privateKey = this.securityRegistry.getServerPrivateKey();
            final PublicKey publicKey = this.securityRegistry.getServerPublicKey();
            if (privateKey != null && publicKey != null) {
                connector.getConfig().setPrivateKey(privateKey, publicKey);
                // TODO this should be automatically done by scandium
                connector.getConfig().setPreferredCipherSuite(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8);
            } else {
                // TODO this should be automatically done by scandium
                connector.getConfig().setPreferredCipherSuite(CipherSuite.TLS_PSK_WITH_AES_128_CCM_8);
            }

            final Endpoint secureEndpoint = new SecureEndpoint(connector);
            coapServer.addEndpoint(secureEndpoint);
            endpoints.add(secureEndpoint);
        	break;
        case SERVER:
				endpoint = new TcpServerEndpoint(new TcpServerConnector((TCPConnectionConfig)config), NetworkConfig.getStandard());
        	break;
        default:
        	throw new IllegalArgumentException("A communication role must be passed in, only NODE and SERVER can be used for a LWM2M server");

        }
        coapServer.addEndpoint(endpoint);

        // define /rd resource
        final RegisterResource rdResource = new RegisterResource(new RegistrationHandler(this.clientRegistry,
                this.securityRegistry));
        coapServer.add(rdResource);

        // create sender
        endpoints.add(endpoint);
        // TODO add a way to set timeout.
        requestSender = new CaliforniumLwM2mRequestSender(endpoints, this.clientRegistry, this.observationRegistry,
                modelProvider, COAP_REQUEST_TIMEOUT_MILLIS);
    }

    @Override
    public void start() {

        // Start registries
        if (clientRegistry instanceof Startable) {
            ((Startable) clientRegistry).start();
        }
        if (securityRegistry instanceof Startable) {
            ((Startable) securityRegistry).start();
        }
        if (observationRegistry instanceof Startable) {
            ((Startable) observationRegistry).start();
        }

        // Start server
        coapServer.start();

        LOG.info("LW-M2M server started");
    }

    @Override
    public void stop() {
        // Stop server
        coapServer.stop();

        // Start registries
        if (clientRegistry instanceof Stoppable) {
            ((Stoppable) clientRegistry).stop();
        }
        if (securityRegistry instanceof Stoppable) {
            ((Stoppable) securityRegistry).stop();
        }
        if (observationRegistry instanceof Stoppable) {
            ((Stoppable) observationRegistry).stop();
        }

        LOG.info("LW-M2M server stopped");
    }

    @Override
	public void destroy() {
        // Destroy server
        coapServer.destroy();

        // Destroy registries
        if (clientRegistry instanceof Destroyable) {
            ((Destroyable) clientRegistry).destroy();
        }
        if (securityRegistry instanceof Destroyable) {
            ((Destroyable) securityRegistry).destroy();
        }
        if (observationRegistry instanceof Destroyable) {
            ((Destroyable) observationRegistry).destroy();
        }

        LOG.info("LW-M2M server destroyed");
    }

    @Override
    public ClientRegistry getClientRegistry() {
        return this.clientRegistry;
    }

    @Override
    public ObservationRegistry getObservationRegistry() {
        return this.observationRegistry;
    }

    @Override
    public SecurityRegistry getSecurityRegistry() {
        return this.securityRegistry;
    }

    @Override
    public LwM2mModelProvider getModelProvider() {
        return this.modelProvider;
    }

    @Override
    public <T extends LwM2mResponse> T send(final Client destination, final DownlinkRequest<T> request) {
        return requestSender.send(destination, request);
    }

    @Override
    public <T extends LwM2mResponse> void send(final Client destination, final DownlinkRequest<T> request,
            final ResponseConsumer<T> responseCallback, final ExceptionConsumer errorCallback) {
        requestSender.send(destination, request, responseCallback, errorCallback);
    }

    /**
     * @return the underlying {@link CoapServer}
     */
    public CoapServer getCoapServer() {
        return coapServer;
    }
}
