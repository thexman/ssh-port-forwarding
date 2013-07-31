package org.jvnet.hudson.plugins;

import java.io.Serializable;

public class ForwardingDescriptor implements Serializable{
		
	/**
	 * 
	 */
	private static final long serialVersionUID = -2811519477358799637L;
	
	private int localPort;
	private String remoteHost;
	private int remotePort;
	
	public ForwardingDescriptor(int localPort, String remoteHost, int remotePort) {
		super();
		this.localPort = localPort;
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
	}

	public int getLocalPort() {
		return localPort;
	}

	public String getRemoteHost() {
		return remoteHost;
	}

	public int getRemotePort() {
		return remotePort;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + localPort;
		result = prime * result
				+ ((remoteHost == null) ? 0 : remoteHost.hashCode());
		result = prime * result + remotePort;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ForwardingDescriptor other = (ForwardingDescriptor) obj;
		if (localPort != other.localPort)
			return false;
		if (remoteHost == null) {
			if (other.remoteHost != null)
				return false;
		} else if (!remoteHost.equals(other.remoteHost))
			return false;
		if (remotePort != other.remotePort)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ForwardingDescriptor [localPort=" + localPort + ", remoteHost="
				+ remoteHost + ", remotePort=" + remotePort + "]";
	}
	
	
	public String toDisplayString() {
		return String.format("Forwarding local port %d to %s:%d", localPort, remoteHost, remotePort);
	}
	
}
