package org.eclipse.leshan.server.californium.impl;

import java.net.InetSocketAddress;

import org.eclipse.californium.elements.config.UDPConnectionConfig;

public class LeshanUDPConnnectionConfig extends UDPConnectionConfig{
	
	private final InetSocketAddress localAddress;
	private final InetSocketAddress localAddressSecure;

	public LeshanUDPConnnectionConfig(final InetSocketAddress localAddress, final InetSocketAddress localAddressSecure) {
		this.localAddress = localAddress;
		this.localAddressSecure = localAddressSecure;
	}

	@Override
	public String getRemoteAddress() {
		return localAddress.getHostName();
	}

	@Override
	public int getRemotePort() {
		return localAddress.getPort();
	}
	
	public String getRemoteAddressSecure() {
		return localAddressSecure.getHostName();
	}

	public int getRemotePortSecure() {
		return localAddressSecure.getPort();
	}
	
	public InetSocketAddress getLocalAddress(){
		return localAddress;
	}
	
	public InetSocketAddress getLocalAddressSecure() {
		return localAddressSecure;
	}

	
}
