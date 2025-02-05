package tukano.impl.rest;

/*import java.net.URI;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import java.util.logging.Logger;

import tukano.impl.Token;
import tukano.impl.svr.ControlResource;
import utils.Args;*/
import utils.IP;
import utils.auth.Authentication;
import utils.auth.ControlResource;
import utils.auth.RequestCookiesCleanupFilter;
import utils.auth.RequestCookiesFilter;
import jakarta.ws.rs.core.Application;
import tukano.impl.Token;

import java.util.HashSet;
import java.util.Set;

public class TukanoRestServer extends Application {
	/*
	 * final private static Logger Log =
	 * Logger.getLogger(TukanoRestServer.class.getName());
	 * 
	 * static final String INETADDR_ANY = "0.0.0.0";
	 */
	static String SERVER_BASE_URI = "http://%s:%s/rest";

	public static final int PORT = 8080;

	public static String serverURI;

	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	public TukanoRestServer() {
		serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
		resources.add(RestBlobsResource.class);
		resources.add(RestUsersResource.class);
		resources.add(RestShortsResource.class);
		resources.add(ControlResource.class);
		resources.add(RequestCookiesFilter.class);
		resources.add(RequestCookiesCleanupFilter.class);
		resources.add(Authentication.class);

		singletons.add(new RestBlobsResource());
		singletons.add(new RestShortsResource());
		singletons.add(new RestUsersResource());
		Token.setSecret("58119");

	}

	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}

	/*
	 * protected TukanoRestServer() {
	 * serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
	 * }
	 */

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	/*
	 * protected void start() throws Exception {
	 * 
	 * ResourceConfig config = new ResourceConfig();
	 * 
	 * config.register(RestBlobsResource.class);
	 * config.register(RestUsersResource.class);
	 * config.register(RestShortsResource.class);
	 * 
	 * JdkHttpServerFactory.createHttpServer(
	 * URI.create(serverURI.replace(IP.hostname(), INETADDR_ANY)), config);
	 * 
	 * Log.info(String.format("Tukano Server ready @ %s\n", serverURI));
	 * }
	 */
	public static void main(String[] args) throws Exception {
		// Args.use(args);

		// Token.setSecret( Args.valueOf("-secret", ""));
		// Props.load( Args.valueOf("-props", "").split(","));

		// new TukanoRestServer().start();
		return;
	}
}
