package it.unipi.ase.tamer.teams.security;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.Optional;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Enforces {@link AccessPolicy} on every matched business endpoint after JWT authentication and
 * before bean validation / resource execution (INFRA-10).
 * Reference implementation: copied verbatim (apart from the package) into every service.
 */
public class AccessPolicyFilter {

    @Inject
    AccessPolicy policy;

    @Inject
    SecurityIdentity identity;

    @ServerRequestFilter(priority = Priorities.AUTHORIZATION)
    public void enforce(ContainerRequestContext request) {
        String rawPath = request.getUriInfo().getPath();
        if (rawPath == null) {
            return;
        }
        String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        // Quarkus management endpoints and test-only /probe routes are outside the business policy
        if (path.startsWith("/q/") || path.startsWith("/probe/") || "/probe".equals(path)) {
            return;
        }
        Optional<AccessPolicy.CompiledRule> matched = policy.match(request.getMethod(), path);
        if (matched.isEmpty()) {
            // Fail closed: a newly mapped business endpoint without a policy rule is never implicitly public
            if (identity.isAnonymous()) {
                throw new UnauthorizedException("Missing, invalid or expired token.");
            }
            throw new ForbiddenException("Endpoint not permitted by access policy.");
        }
        AccessPolicy.CompiledRule rule = matched.get();
        if (!rule.isRoleProtected()) {
            return;
        }
        if (identity.isAnonymous()) {
            throw new UnauthorizedException("Missing, invalid or expired token.");
        }
        if (!rule.allowsAnyRole(identity.getRoles())) {
            throw new ForbiddenException("Insufficient role for operation.");
        }
    }
}
