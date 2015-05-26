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

package org.eclipse.leshan.integration.tests;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.elements.ConnectorBuilder.CommunicationRole;
import org.eclipse.leshan.client.LwM2mClient;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.server.Server;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.server.LwM2mServer;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.californium.impl.SecureEndpoint;
import org.eclipse.leshan.server.client.Client;
import org.eclipse.leshan.server.impl.SecurityRegistryImpl;

/**
 * Helper for running a server and executing a client against it.
 * 
 */
public class IntegrationTestHelper {

    static final String ENDPOINT_IDENTIFIER = "kdfflwmtm";

    LwM2mServer server;
    LwM2mClient client;

    public void createClient() {
        final LeshanClientBuilder builder = new LeshanClientBuilder();
        builder.setRemoteServer(Server.createNoSecServer(getServerAddress()));

        client = builder.build(2, 3);
    }

    public void createServer() {
        final LeshanServerBuilder builder = new LeshanServerBuilder();
        builder.setLocalAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        builder.setLocalAddressSecure(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        builder.setSecurityRegistry(new SecurityRegistryImpl() {
            // TODO we should separate SecurityRegistryImpl in 2 registries :
            // InMemorySecurityRegistry and PersistentSecurityRegistry

            @Override
            protected void loadFromFile() {
                // do not load From File
            }

            @Override
            protected void saveToFile() {
                // do not save to file
            }
        });
        server = builder.build();
    }

    Client getClient() {
        return server.getClientRegistry().get(ENDPOINT_IDENTIFIER);
    }

    protected InetSocketAddress getServerSecureAddress() {
        for (final Endpoint endpoint : ((LeshanServer) server).getCoapServer().getEndpoints()) {
            if (endpoint instanceof SecureEndpoint)
                return endpoint.getAddress();
        }
        return null;
    }

    protected InetSocketAddress getServerAddress() {
        for (final Endpoint endpoint : ((LeshanServer) server).getCoapServer().getEndpoints()) {
            if (!(endpoint instanceof SecureEndpoint))
                return endpoint.getAddress();
        }
        return null;
    }
}
