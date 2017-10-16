package br.com.caelum.vraptor.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;

public class VRaptorServer {

	private final Server server;
	private final ContextHandlerCollection contexts;
    private static final String DEFAULT_KEYSTORE_PATH = "WEB-INF/classes/jetty.jks";
    private static final String DEFAULT_KEYSTORE_PASSWORD = "";

	public VRaptorServer(String webappDirLocation, String webXmlLocation) {
		boolean useSecureServer = Boolean.parseBoolean(System.getProperty("server.https", "false"));
		if (useSecureServer) {
			this.server = createSecureServer();
		} else {
			this.server = createServer();
		}
		
		this.contexts = new ContextHandlerCollection();
		reloadContexts(webappDirLocation, webXmlLocation);
	}

	private void reloadContexts(String webappDirLocation, String webXmlLocation) {
		WebAppContext context = loadContext(webappDirLocation, webXmlLocation);
		if ("development".equals(getEnv())) {
			contexts.setHandlers(new Handler[]{context, systemRestart()});
		} else {
			contexts.setHandlers(new Handler[]{context});
		}
	}

	public void start() throws Exception {
		server.setHandler(contexts);
		if (server.isStarted()) server.stop();
		server.start();
	}

	private static WebAppContext loadContext(String webappDirLocation, String webXmlLocation) {
		WebAppContext context = new WebAppContext();
		context.setContextPath(getContext());
		context.setDescriptor(webXmlLocation);
		context.setResourceBase(webappDirLocation);
		context.setParentLoaderPriority(true);
		return context;
	}

	private static String getContext() {
		return System.getProperty("vraptor.context", "/");
	}

	private ContextHandler systemRestart() {
		AbstractHandler system = new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest,
					HttpServletRequest request, HttpServletResponse response)
					throws IOException, ServletException {
				restartContexts();
				response.setContentType("text/html;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				baseRequest.setHandled(true);
				response.getWriter().println("<h1>Done</h1>");
			}
		};
		ContextHandler context = new ContextHandler();
		context.setContextPath("/vraptor/restart");
		context.setResourceBase(".");
		context.setClassLoader(Thread.currentThread().getContextClassLoader());
		context.setHandler(system);
		return context;
	}

	private String getEnv() {
		String envVar = System.getenv("VRAPTOR_ENV");
		String environment = envVar != null? envVar : System.getProperty("br.com.caelum.vraptor.environment", "development");
		return environment;
	}

	void restartContexts() {
		try {
			contexts.stop();
			contexts.start();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
    private static Server createSecureServer() {
        Server server = new Server();
        SslContextFactory sslContextFactory = new SslContextFactory(getKeyStorePath());
        sslContextFactory.setKeyStorePassword(getKeyStorePassword());
        sslContextFactory.addExcludeProtocols("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1");
        sslContextFactory.addExcludeCipherSuites(
        		"TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
        		"SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
        		"SSL_RSA_WITH_RC4_128_SHA",
        		"TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
        		"TLS_ECDHE_RSA_WITH_RC4_128_SHA",
        		"TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
        		"TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
        		"TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
        		"TLS_ECDH_RSA_WITH_RC4_128_SHA",
        		"TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
        		"SSL_RSA_WITH_RC4_128_MD5",
        		"SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
        		"SSL_RSA_WITH_3DES_EDE_CBC_SHA");
        
        SslSocketConnector connector = new SslSocketConnector(sslContextFactory);
        connector.setPort(Integer.valueOf(getPort("8443")));
        server.setConnectors(new Connector[] { connector });
        
        String webHost = getHost();
        if (webHost == null || webHost.isEmpty()) {
            webHost = System.getProperty("server.host", "0.0.0.0");
        }
        server.getConnectors()[0].setHost(webHost);
        server.setAttribute("jetty.host", webHost);
        
        return server;
    }

	private static Server createServer() {
		String webPort = getPort("8080");
		Server server = new Server(Integer.valueOf(webPort));
		String webHost = getHost();
		server.getConnectors()[0].setHost(webHost);
		server.setAttribute("jetty.host", webHost);
		return server;
	}

	private static String getPort(String defaultPort) {
		String port = System.getenv("PORT");
		if (port == null || port.isEmpty()) {
			port = System.getProperty("server.port", defaultPort);
		}
		return port;
	}
	
	private static String getHost() {
		String host = System.getenv("HOST");
		if (host == null || host.isEmpty()) {
			host = System.getProperty("server.host", "0.0.0.0");
		}
		return host;
	}
	
	private static String getKeyStorePath() {
		return System.getProperty("server.https.keystore.path", DEFAULT_KEYSTORE_PATH);
	}

	private static String getKeyStorePassword() {
		return System.getProperty("server.https.keystore.password", DEFAULT_KEYSTORE_PASSWORD);
	}
	
	public void stop() {
		try {
			this.server.stop();
		} catch (Exception e) {
			throw new RuntimeException("Could not stop server", e);
		}
	}
}
