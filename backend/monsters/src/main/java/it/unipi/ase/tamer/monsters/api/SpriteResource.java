package it.unipi.ase.tamer.monsters.api;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Sprites are public so ordinary browser image elements can load them without bearer tokens. */
@Path("/images/monsters")
@PermitAll
@Tag(name = "sprites")
public class SpriteResource {

    private static final String SPRITE_PREFIX = "seed/images/monsters/";

    @GET
    @Path("/{filename}")
    @Operation(summary = "Serve a cacheable 64x64 monster sprite (public)")
    @APIResponse(responseCode = "200", description = "PNG image (no token needed, P-16)")
    @APIResponse(responseCode = "404", description = "Unknown sprite")
    public Response sprite(@PathParam("filename") String filename) {
        return loadSprite(filename, true);
    }

    @HEAD
    @Path("/{filename}")
    @Operation(summary = "Check cacheable monster sprite headers (public)")
    @APIResponse(responseCode = "200", description = "PNG image headers (no token needed, P-16)")
    @APIResponse(responseCode = "404", description = "Unknown sprite")
    public Response spriteHead(@PathParam("filename") String filename) {
        return loadSprite(filename, false);
    }

    private Response loadSprite(String filename, boolean includeBody) {
        if (filename == null || !filename.matches("[0-9]{3}\\.png")) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try (InputStream png = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(SPRITE_PREFIX + filename)) {
            if (png == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            byte[] bytes = png.readAllBytes();
            Response.ResponseBuilder builder = Response.ok()
                    .type("image/png")
                    .header("Cache-Control", "public, max-age=86400")
                    .header("Content-Length", bytes.length);
            if (includeBody) {
                builder.entity(bytes);
            }
            return builder.build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
