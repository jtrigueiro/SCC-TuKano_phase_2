package tukano.impl.svr;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Class with control endpoints.
 */
@Path("/ctrlold")
public class ControlResource {

	/**
	 * This methods just prints a string. It may be useful to check if the current
	 * version is running on Azure.
	 */
	@Path("/version")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String version() {
		return "v: 0001";
	}

}
