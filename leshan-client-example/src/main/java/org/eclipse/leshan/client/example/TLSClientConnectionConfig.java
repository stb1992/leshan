package org.eclipse.leshan.client.example;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.eclipse.californium.core.network.config.DefaultTCPConnectionConfig;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;

public class TLSClientConnectionConfig extends DefaultTCPConnectionConfig{

	private ConnectionStateListener connectionListener;

	public TLSClientConnectionConfig(final String address, final int port) {
		super(CommunicationRole.CLIENT, address, port);
	}
	
	public void secure() throws SSLException, NoSuchAlgorithmException, KeyManagementException {
		final SSLContext context = SSLContext.getInstance("TLSV1.2");
		context.init(null, null, null);
		setClientSSL(context);
	}
	
	public void setConnectionListener(final ConnectionStateListener listener) {
		this.connectionListener = listener;
	}
	
	@Override
	public ConnectionStateListener getListener() {
		return connectionListener;
	}

}
