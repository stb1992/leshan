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
package org.eclipse.leshan.server.californium;

import io.netty.channel.ChannelOption;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.EnumSet;

import javax.net.ssl.SSLContext;

import org.eclipse.californium.elements.config.ConnectionConfig;
import org.eclipse.californium.elements.config.ConnectionConfig.CommunicationRole;
import org.eclipse.californium.elements.config.TCPConnectionConfig.SSLCLientCertReq;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.server.LwM2mServer;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.californium.impl.LeshanTCPConnectionConfig;
import org.eclipse.leshan.server.californium.impl.LeshanUDPConnnectionConfig;
import org.eclipse.leshan.server.client.ClientRegistry;
import org.eclipse.leshan.server.impl.ClientRegistryImpl;
import org.eclipse.leshan.server.impl.ObservationRegistryImpl;
import org.eclipse.leshan.server.impl.SecurityRegistryImpl;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.model.StandardModelProvider;
import org.eclipse.leshan.server.observation.ObservationRegistry;
import org.eclipse.leshan.server.security.SecurityRegistry;
import org.eclipse.leshan.util.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class helping you to build and configure a Californium based Leshan Lightweight M2M server. Usage: create it, call
 * the different setters for changing the configuration and then call the {@link #build()} method for creating the
 * {@link LwM2mServer} ready to operate.
 */
public class LeshanServerBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(LeshanServer.class);

	/** IANA assigned UDP port for CoAP (so for LWM2M) */
	public static final int PORT = 5683;

	/** IANA assigned UDP port for CoAP with DTLS (so for LWM2M) */
	public static final int PORT_DTLS = 5684;    

	public static LeshanTCPServerBuilder<?> getLeshanTCPServerBuilder() {
		return new LeshanTCPServerBuilder();

	}

	public static LeshanTLSServerBuilder getLeshanTLSServerBuilder() {
		return new LeshanTLSServerBuilder();
	}

	public static LeshanUDPServerBuilder getLeshanUDPServerBuilder() {
		return new LeshanUDPServerBuilder();

	}

	public static abstract class BasicLeshanServerBuilder<E extends BasicLeshanServerBuilder<E>> {

		private final EnumSet<BindingMode> bindingModes;

		protected SecurityRegistry securityRegistry;
		protected ObservationRegistry observationRegistry;
		protected ClientRegistry clientRegistry;
		protected LwM2mModelProvider modelProvider;

		private BasicLeshanServerBuilder(final BindingMode bMode){
			bindingModes = BindingMode.getBindingMode(bMode);
		}

		public E setClientRegistry(final ClientRegistry clientRegistry) {
			this.clientRegistry = clientRegistry;
			return (E)this;
		}

		public E setObservationRegistry(final ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return (E) this;
		}

		public E setSecurityRegistry(final SecurityRegistry securityRegistry) {
			this.securityRegistry = securityRegistry;
			return (E) this;
		}

		public E setObjectModelProvider(final LwM2mModelProvider objectModelProvider) {
			this.modelProvider = objectModelProvider;
			return (E)this;
		}

		public LeshanServer build() {
			if (clientRegistry == null)
				clientRegistry = new ClientRegistryImpl();
			if (securityRegistry == null)
				securityRegistry = new SecurityRegistryImpl();
			if (observationRegistry == null)
				observationRegistry = new ObservationRegistryImpl();
			if (modelProvider == null) 
				modelProvider = new StandardModelProvider();

			if(bindingModes.contains(BindingMode.Q) || bindingModes.contains(BindingMode.S)) {
				LOG.info("Binding Modes Q and S are not supported in this verison, they will be omitted");
			}

			final ConnectionConfig connectionConfig = buildConnectionConfig();

			return new LeshanServer(clientRegistry, securityRegistry, observationRegistry, modelProvider, connectionConfig);
		}

		protected abstract ConnectionConfig buildConnectionConfig();
	}

	public static class LeshanTCPServerBuilder<T extends LeshanTCPServerBuilder<T>> extends BasicLeshanServerBuilder<T>{

		protected final LeshanTCPConnectionConfig connectionConfig;

		public LeshanTCPServerBuilder(){
			super(BindingMode.T);
			this.connectionConfig = new LeshanTCPConnectionConfig(CommunicationRole.SERVER);
		}

		public T setConnectionStateListener(final ConnectionStateListener connectionStateListener) {
			connectionConfig.setConnectionStateListener(connectionStateListener);
			return (T)this;
		}

		public <O> T addChannelOption(final ChannelOption<O> option, final O value) {
			connectionConfig.addChannelOption(option, value);
			return (T)this;
		}

		public T setLocalAddress(final String address) {
			connectionConfig.setAddress(address);
			return (T)this;
		}

		public T setPort(final int port) {
			connectionConfig.setPort(port);
			return (T)this;
		}

		@Override
		protected ConnectionConfig buildConnectionConfig() {
			if (connectionConfig.getRemoteAddress() == null)
				connectionConfig.setAddress("127.0.0.1");
			if (connectionConfig.getRemotePort() == 0);
			connectionConfig.setPort(PORT);
			return connectionConfig;
		}
	}

	public static class LeshanTLSServerBuilder extends LeshanTCPServerBuilder<LeshanTLSServerBuilder>{

		protected SSLContext sslContext;
		protected SSLCLientCertReq req;
		protected String[] supportedTLSVersion;


		public LeshanTLSServerBuilder setSSLContext(final SSLContext sslContext) {
			this.sslContext = sslContext;
			return this;
		}

		public LeshanTLSServerBuilder setSSLClientCertReq(final SSLCLientCertReq req) {
			this.req = req;
			return this;
		}

		public LeshanTLSServerBuilder setSupportedTLSVersion(final String... supportedTLSVersion) {
			this.supportedTLSVersion = supportedTLSVersion;
			return this;
		}

		@Override
		protected ConnectionConfig buildConnectionConfig() {
			Validate.notNull(sslContext, "SSLContext cannot be null is using TLS Connector");
			if(req == null) {
				req = SSLCLientCertReq.NONE;
			}
			if(supportedTLSVersion == null) {
				supportedTLSVersion = new String[0];
			}
			connectionConfig.setServerSSL(sslContext, req, supportedTLSVersion);
			return connectionConfig;
		}    
	}

	public static class LeshanUDPServerBuilder extends BasicLeshanServerBuilder<LeshanUDPServerBuilder>{

		private InetSocketAddress localAddress;
		private InetSocketAddress localAddressSecure;

		private LeshanUDPServerBuilder(){
			super(BindingMode.U);
		}


		public LeshanUDPServerBuilder setLocalAddress(final String hostname, final int port) {
			this.localAddress = new InetSocketAddress(hostname, port);
			return this;
		}

		public LeshanUDPServerBuilder setLocalAddressSecure(final String hostname, final int port) {
			this.localAddressSecure = new InetSocketAddress(hostname, port);
			return this;
		}

		@Override
		protected ConnectionConfig buildConnectionConfig() {
			if (localAddress == null)
				localAddress = new InetSocketAddress((InetAddress) null, PORT);
			if (localAddressSecure == null)
				localAddressSecure = new InetSocketAddress((InetAddress) null, PORT_DTLS);
			return new LeshanUDPConnnectionConfig(localAddress, localAddressSecure);
		}
	}
}
