package it.unipi.ase.tamer.auth.api;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import it.unipi.ase.tamer.auth.error.ErrorCodes;
import it.unipi.ase.tamer.auth.error.ErrorResponse;
import it.unipi.ase.tamer.auth.security.AccessPolicy;
import it.unipi.ase.tamer.auth.service.TokenIssuer;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Private gateway authorization adapter invoked by Nginx {@code auth_request} (INFRA-9, P-23).
 * Evaluates the forwarded bearer JWT and {@code (X-Original-Method, X-Original-URI)} against the
 * canonical {@link AccessPolicy}.
 */
@Path("/internal/auth/authorize")
@Tag(name = "auth-internal")
public class AuthorizeResource {

    public static final String HEADER_ORIGINAL_METHOD = "X-Original-Method";
    public static final String HEADER_ORIGINAL_URI = "X-Original-URI";
    public static final String HEADER_AUTH_SUBJECT = "X-Auth-Subject";

    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    JWTParser parser;

    @Inject
    AccessPolicy policy;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Gateway auth_request adapter (INFRA-9)",
            description = "Verifies the forwarded Bearer JWT and checks (X-Original-Method, X-Original-URI) against the canonical access policy.")
    @APIResponse(responseCode = "204", description = "Request allowed; sets X-Auth-Subject on role-protected routes")
    @APIResponse(responseCode = "401", description = "Missing, invalid or expired bearer token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden route, invalid path, or insufficient role/audience",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response authorize(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                              @HeaderParam(HEADER_ORIGINAL_METHOD) String originalMethod,
                              @HeaderParam(HEADER_ORIGINAL_URI) String originalUri) {
        if (isBlank(originalMethod) || isBlank(originalUri)) {
            return forbidden();
        }
        String canonical = AccessPolicy.canonicalizePath(originalUri);
        if (canonical == null || isPrivatePath(canonical)) {
            return forbidden();
        }
        Optional<AccessPolicy.CompiledRule> matched = policy.match(originalMethod, canonical);
        if (matched.isEmpty()) {
            return forbidden();
        }
        AccessPolicy.CompiledRule rule = matched.get();
        if ("public".equals(rule.access())) {
            return noStore(Response.noContent()).build();
        }
        if (!rule.isRoleProtected()) {
            return forbidden();
        }

        JsonWebToken jwt = parseBearer(authorization);
        if (jwt == null) {
            return unauthorized();
        }

        String subject = jwt.getSubject();
        if (isBlank(subject) || jwt.getClaim(Claims.exp.name()) == null
                || !TokenIssuer.ISSUER.equals(jwt.getIssuer())) {
            return unauthorized();
        }

        if (!rule.allowsAnyRole(jwt.getGroups()) || !audienceMatches(rule, jwt.getAudience())) {
            return forbidden();
        }

        return noStore(Response.noContent())
                .header(HEADER_AUTH_SUBJECT, subject)
                .build();
    }

    private JsonWebToken parseBearer(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String raw = authorization.substring(BEARER_PREFIX.length());
        if (raw.isEmpty() || raw.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        try {
            return parser.parse(raw);
        } catch (ParseException e) {
            return null;
        }
    }

    private static boolean audienceMatches(AccessPolicy.CompiledRule rule, Set<String> audience) {
        if (audience == null || audience.isEmpty()) {
            return false;
        }
        if (rule.roles().contains(TokenIssuer.ROLE_PLAYER)) {
            return audience.contains(TokenIssuer.AUDIENCE_API);
        }
        if (rule.roles().contains(TokenIssuer.ROLE_SERVICE)) {
            return audience.contains(TokenIssuer.AUDIENCE_INTERNAL);
        }
        return false;
    }

    private static boolean isPrivatePath(String canonicalPath) {
        return "/internal".equals(canonicalPath) || canonicalPath.startsWith("/internal/")
                || "/q".equals(canonicalPath) || canonicalPath.startsWith("/q/");
    }

    private static Response unauthorized() {
        return noStore(Response.status(401)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(ErrorCodes.UNAUTHORIZED, "Missing, invalid or expired token.")))
                .build();
    }

    private static Response forbidden() {
        return noStore(Response.status(403)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(ErrorCodes.FORBIDDEN, "Not allowed.")))
                .build();
    }

    private static Response.ResponseBuilder noStore(Response.ResponseBuilder builder) {
        return builder.header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
