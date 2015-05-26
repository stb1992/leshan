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

package org.eclipse.leshan.client.server;

import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.leshan.core.model.SecurityMode;
import org.eclipse.leshan.util.Validate;

/**
 * An immutable structure which represent a LW-M2M server from the perspective of a Leshan LW-M2M client.
 */
public class Server {

    private InetSocketAddress serverAddress;
    private final Set<SecurityMode> securityModes = new HashSet<SecurityMode>();
    private String pskIdentity;
    private byte[] pskKey;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    private Server(InetSocketAddress serverAddress, SecurityMode... securityModes) {
        Validate.notNull(serverAddress);
        Validate.notNull(securityModes);

        this.serverAddress = serverAddress;
        this.securityModes.addAll(Arrays.asList(securityModes));
    }

    /**
     * The IP Address of the LW-M2M server that will be the destination of all requests initiated by the client.
     * 
     * @return
     */
    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    /**
     * The security modes which communication to be used between the client and the LW-M2M server. See section 7.1 of
     * the LW-M2M specification for more details on the valid security modes.
     * 
     * @return
     */
    public Set<SecurityMode> getSecurityModes() {
        return securityModes;
    }

    /**
     * The private key for use with Raw-Public Key (RPK) Certificates.
     * 
     * @return
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * The private key for use with Raw-Public Key (RPK) Certificates.
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * The Pre-Shared Keys (PSK) Identity.
     * 
     * @return
     */
    public String getPskIdentity() {
        return pskIdentity;
    }

    /**
     * The Pre-Shared Keys (PSK) Key.
     * 
     * @return
     */
    public byte[] getPskKey() {
        return pskKey;
    }

    /**
     * Create a connection to a LW-M2M server using no security.
     * 
     * @param serverAddress the remote server.
     * @return
     */
    public static Server createNoSecServer(InetSocketAddress serverAddress) {
        Server server = new Server(serverAddress, SecurityMode.NO_SEC);

        return server;
    }

    /**
     * Create a connection to a LW-M2M server using only PSK.
     * 
     * @param serverAddress the remote server.
     * @param pskIdentity the PSK Identity to use.
     * @param pskKey the PSK key to use.
     * @return
     */
    public static Server createPskServer(InetSocketAddress serverAddress, String pskIdentity, byte[] pskKey) {
        Server server = new Server(serverAddress, SecurityMode.PSK);
        server.pskIdentity = pskIdentity;
        server.pskKey = pskKey;

        return server;
    }

    /**
     * Create a connection to a LW-M2M server using only RPK.
     * 
     * @param serverAddress the remote server.
     * @param clientPrivateKey the Private RPK key to use.
     * @param clientPublicKey thePublic RPK key to use.
     * @return
     */
    public static Server createRpkServer(InetSocketAddress serverAddress, PrivateKey clientPrivateKey,
            PublicKey clientPublicKey) {
        Server server = new Server(serverAddress, SecurityMode.RPK);
        server.privateKey = clientPrivateKey;
        server.publicKey = clientPublicKey;

        return server;
    }

    /**
     * Create a connection to a LW-M2M server using both RPK and PSK security.
     * 
     * @param serverAddress the remote server.
     * @param pskIdentity the PSK Identity to use.
     * @param pskKey the PSK key to use.
     * @param clientPrivateKey the Private RPK key to use.
     * @param clientPublicKey thePublic RPK key to use.
     * @return
     */
    public static Server createPskAndRpkServer(InetSocketAddress serverAddress, String pskIdentity, byte[] pskKey,
            PrivateKey clientPrivateKey, PublicKey clientPublicKey) {
        Server server = new Server(serverAddress, SecurityMode.PSK, SecurityMode.RPK);
        server.pskIdentity = pskIdentity;
        server.pskKey = pskKey;
        server.privateKey = clientPrivateKey;
        server.publicKey = clientPublicKey;

        return server;
    }

}
