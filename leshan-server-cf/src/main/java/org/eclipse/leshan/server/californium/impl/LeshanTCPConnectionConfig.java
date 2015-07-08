package org.eclipse.leshan.server.californium.impl;

import org.eclipse.californium.elements.config.TCPConnectionConfig;

public class LeshanTCPConnectionConfig extends TCPConnectionConfig{

	private String address;
	private int port;

	public LeshanTCPConnectionConfig(final CommunicationRole role) {
		super(role);
	}

	public void setAddress(final String address) {
		this.address = address;
	}

	public void setPort(final int port) {
		this.port = port;
	}

	@Override
	public String getRemoteAddress() {
		return address;
	}

	@Override
	public int getRemotePort() {
		return port;
	}

}
