package org.eclipse.leshan.server.californium.impl;

import org.eclipse.californium.elements.config.TCPConnectionConfig;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;

public class LeshanTCPConnectionConfig extends TCPConnectionConfig{

	private String address;
	private int port;
	private ConnectionStateListener listener;



	public LeshanTCPConnectionConfig(final CommunicationRole role) {
		super(role);
	}
	
	
	public void setAddress(final String address) {
		this.address = address;
	}

	public void setPort(final int port) {
		this.port = port;
	}
	
	public void setConnectionStateListener(final ConnectionStateListener listener) {
		this.listener = listener;
	}

	@Override
	public String getRemoteAddress() {
		return address;
	}

	@Override
	public int getRemotePort() {
		return port;
	}
	
	@Override
	public ConnectionStateListener getListener() {
		return listener;
	}
}
