package org.jvnet.hudson.plugins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

public final class PortForwardingBuildWrapper extends BuildWrapper {

	public static final Logger LOGGER = Logger.getLogger(PortForwardingBuildWrapper.class.getName());
	

	private String host = "";
	private int port = 22;
	private String username = "";
	private String password = "";
	private String keyfile = "";
	private int serverAliveInterval = 0;
	final List<ForwardingDescriptor> portForwardings = new ArrayList<ForwardingDescriptor>();	
	private String portForwardingsStr = "";
	
	
	public PortForwardingBuildWrapper() {
	}

	@DataBoundConstructor
	public PortForwardingBuildWrapper(String host, String port, String username, String password, String keyfile, String serverAliveInterval, String portForwardingsStr) throws IOException {
		this.host = host;
		this.port = parsePort(port); 
		this.username = username;
		this.password = password;
		this.keyfile = keyfile;
		try {
			this.serverAliveInterval = Integer.parseInt(serverAliveInterval);
		} catch (NumberFormatException ex) {
			this.serverAliveInterval = 0;
		}
		setPortForwardingsStr(portForwardingsStr);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		final PrintStream logger = listener.getLogger();
		JSch.setLogger( createSshLogger(logger) );
		final Session session = forwardPorts(logger);
		 
		Environment env = null;
		if (session != null) { 
			// we are able to connect to remote host			
			env = new Environment() {				
				@Override
				public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
					logger.println("Closing SSH session to " + host);
					closeSession(session);
					JSch.setLogger( null );
					return true;
				}
			};			
		} else {
			// build will fail.
			env = null;
		}
		
		return env;
	}

	

	private int parsePort(final String portStr) throws RuntimeException {
		try {
			int port = Integer.parseInt(portStr);
			if (port < 1 || port > 65535) {
				throw new RuntimeException("Invalid port number. Enter value between 1 and 65535");
			}
			return port;
		} catch (final NumberFormatException ex) {
			throw new RuntimeException("Invalid port number", ex);
		}
	}
	
	public String getPortForwardingsStr() {
		return portForwardingsStr;
	}

	public void setPortForwardingsStr(String portForwardingsStr) {
		if (portForwardingsStr != null) {
			String[] forwardings = portForwardingsStr.trim().split(";");
			for(final String forwarding: forwardings) {
				final String[] items =  forwarding.split(":");
				if (items.length != 3) {
					throw new RuntimeException("Invalid string format. Use localPort:remoteHost:remotePort pattern");
				}		
				int localPort = parsePort(items[0].trim());
				int remotePort = parsePort(items[2].trim());
				final ForwardingDescriptor d = new ForwardingDescriptor(localPort, items[1].trim(), remotePort);			
				portForwardings.add(d);
			}
		}
		this.portForwardingsStr = portForwardingsStr;
	}
	
	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getKeyfile() {
		return keyfile;
	}

	public int getServerAliveInterval() {
		return serverAliveInterval;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}

	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	public static final class DescriptorImpl extends BuildWrapperDescriptor {

		public DescriptorImpl() {
			super(PortForwardingBuildWrapper.class);
			load();
		}

		protected DescriptorImpl(Class<? extends BuildWrapper> clazz) {
			super(clazz);
		}

		@Override
		public String getDisplayName() {
			return "Port Forwarding Plugin";
		}

		public String getShortName() {
			return "[PortForwarding] ";
		}

		@Override
		public String getHelpFile() {
			return "/plugin/ssh/help.html";
		}

		@Override
		public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) {
			return req.bindJSON(clazz, formData);
		}
		

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) {			
			save();
			return true;
		}

		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return true;
		}

	}	

	private void log(final PrintStream logger, final String message) {
		logger.println(StringUtils.defaultString(DESCRIPTOR.getShortName()) + message);
	}
	
	private Session createSession() throws JSchException {
		JSch jsch = new JSch();		
		Session session = jsch.getSession(username, host, port);
		session.setDaemonThread(true);
		if (this.keyfile != null && this.keyfile.length() > 0) {
			jsch.addIdentity(this.keyfile, this.password);
		} else {
			session.setPassword(password);
		}

		UserInfo ui = new SSHUserInfo(password);
		session.setUserInfo(ui);

		session.setServerAliveInterval(serverAliveInterval);

		java.util.Properties config = new java.util.Properties();
		config.put("StrictHostKeyChecking", "no");
		session.setConfig(config);
		session.connect();

		return session;
	}

	public Session forwardPorts(final PrintStream logger) throws InterruptedException {		
		try {
			log(logger, String.format("Creating session to %s@%s:%d", username, host, port));						
			
			final Session session = createSession();			
			for(final ForwardingDescriptor d : portForwardings) {												
				log(logger, d.toDisplayString());															
				session.setPortForwardingL(d.getLocalPort(), d.getRemoteHost(), d.getRemotePort());
			}			
						
			return session;			
		} catch (final JSchException ex) {
			logger.println(DESCRIPTOR.getShortName() + " Exception:" + ex.getMessage());
			ex.printStackTrace(logger);
			return null;
		}		
	}

	private com.jcraft.jsch.Logger createSshLogger(final PrintStream logger) {
		final com.jcraft.jsch.Logger sshLogger = new com.jcraft.jsch.Logger() {				
			public void log(int level, String message) {
				logger.println(message);
			}
			
			public boolean isEnabled(int level) {
				return true;
			}
		};
		return sshLogger;
	}
	
	public static void closeSession(Session session) {		
		if (session != null) {
			session.disconnect();
		}
	}
}
