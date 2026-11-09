package it.unipi.ase.tamer.monsters.api;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Sprites are public so ordinary browser image elements can load them without bearer tokens. */
@Path("/images/monsters")
@PermitAll
@Tag(name = "sprites")
public class SpriteResource {

    @GET
    @Path("/{filename}")
    @Operation(summary = "Serve a cacheable 64x64 monster sprite (public)")
    public Response sprite(@PathParam("filename") String filename) {
        if (filename == null || !filename.matches("[0-9]{3}\\.png")) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        InputStream png = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/resources/images/monsters/" + filename);
        if (png == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(png, "image/png")
                .header("Cache-Control", "public, max-age=86400").build();
    }
}
