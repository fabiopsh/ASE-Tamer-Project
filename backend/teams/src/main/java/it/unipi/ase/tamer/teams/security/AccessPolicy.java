package it.unipi.ase.tamer.teams.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Loads and evaluates the canonical {@code security/access-policy.json} table (INFRA-10, P-24).
 * Reference implementation: copied verbatim (apart from the package) into every service.
 */
@Startup
@ApplicationScoped
public class AccessPolicy {

    public static final String RESOURCE_PATH = "security/access-policy.json";

    private static final Set<String> ALLOWED_ACCESS = Set.of(
            "public", "public_credential", "client_credentials", "bearer_adapter", "roles");

    private List<CompiledRule> rules = List.of();

    @PostConstruct
    void init() {
        this.rules = loadFromClasspath();
    }

    public static List<CompiledRule> loadFromClasspath() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing access policy resource: " + RESOURCE_PATH);
            }
            return parse(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read access policy resource: " + RESOURCE_PATH, e);
        }
    }

    static List<CompiledRule> parse(InputStream in) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        PolicyFile file = mapper.readValue(in, PolicyFile.class);
        if (file == null || file.rules == null || file.rules.isEmpty()) {
            throw new IllegalStateException("Access policy contains no rules.");
        }
        List<CompiledRule> compiled = new ArrayList<>(file.rules.size());
        Set<String> seen = new HashSet<>();
        for (PolicyRule raw : file.rules) {
            CompiledRule rule = compile(raw);
            String key = rule.method() + " " + rule.canonicalShape();
            if (!seen.add(key)) {
                throw new IllegalStateException("Ambiguous duplicate access policy rule: " + key);
            }
            compiled.add(rule);
        }
        return List.copyOf(compiled);
    }

    public List<CompiledRule> rules() {
        return rules;
    }

    /**
     * Canonicalizes {@code rawPath} (strips query string and trailing slash, rejects traversal or
     * encoded separators) and returns the most specific matching rule for {@code method}.
     */
    public Optional<CompiledRule> match(String method, String rawPath) {
        if (method == null || method.isBlank() || rawPath == null || rawPath.isBlank()) {
            return Optional.empty();
        }
        String canonical = canonicalizePath(rawPath);
        if (canonical == null) {
            return Optional.empty();
        }
        String upperMethod = method.toUpperCase(Locale.ROOT);
        String[] segments = splitSegments(canonical);
        CompiledRule best = null;
        for (CompiledRule rule : rules) {
            if (!rule.method().equals(upperMethod)) {
                continue;
            }
            if (rule.matches(segments)) {
                if (best == null || rule.specificity() > best.specificity()) {
                    best = rule;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static String canonicalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return null;
        }
        String path = rawPath;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (!path.startsWith("/") || path.contains("//") || path.contains("\\")
                || lower.contains("%2f") || lower.contains("%5c") || lower.contains("%00")) {
            return null;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String[] segments = splitSegments(path);
        for (String seg : segments) {
            if (".".equals(seg) || "..".equals(seg) || seg.toLowerCase(Locale.ROOT).contains("%2e")) {
                return null;
            }
        }
        return path;
    }

    private static CompiledRule compile(PolicyRule raw) {
        if (raw == null || isBlank(raw.service) || isBlank(raw.method) || isBlank(raw.path) || isBlank(raw.access)) {
            throw new IllegalStateException("Malformed access policy rule: missing required field.");
        }
        if (!ALLOWED_ACCESS.contains(raw.access)) {
            throw new IllegalStateException("Unsupported access mode in policy: " + raw.access);
        }
        String method = raw.method.trim().toUpperCase(Locale.ROOT);
        String canonical = canonicalizePath(raw.path.trim());
        if (canonical == null) {
            throw new IllegalStateException("Invalid path in access policy: " + raw.path);
        }
        Set<String> roles = raw.roles == null ? Set.of() : Set.copyOf(raw.roles);
        if ("roles".equals(raw.access) && roles.isEmpty()) {
            throw new IllegalStateException("Role-based policy rule requires non-empty roles: " + method + " " + canonical);
        }
        String[] segments = splitSegments(canonical);
        boolean[] isParam = new boolean[segments.length];
        int specificity = 0;
        StringBuilder shape = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            boolean param = seg.startsWith("{") && seg.endsWith("}") && seg.length() > 2;
            isParam[i] = param;
            if (!param) {
                specificity++;
            }
            shape.append('/').append(param ? "{*}" : seg);
        }
        return new CompiledRule(raw.service.trim(), method, canonical, shape.isEmpty() ? "/" : shape.toString(),
                segments, isParam, specificity, raw.access, roles);
    }

    private static String[] splitSegments(String canonicalPath) {
        if ("/".equals(canonicalPath)) {
            return new String[0];
        }
        return canonicalPath.substring(1).split("/");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class PolicyFile {
        public int version;
        public List<PolicyRule> rules;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class PolicyRule {
        public String service;
        public String method;
        public String path;
        public String access;
        public List<String> roles;
    }

    public record CompiledRule(
            String service,
            String method,
            String path,
            String canonicalShape,
            String[] segments,
            boolean[] isParam,
            int specificity,
            String access,
            Set<String> roles) {

        boolean matches(String[] candidate) {
            if (candidate.length != segments.length) {
                return false;
            }
            for (int i = 0; i < segments.length; i++) {
                if (isParam[i]) {
                    if (candidate[i].isEmpty()) {
                        return false;
                    }
                } else if (!segments[i].equals(candidate[i])) {
                    return false;
                }
            }
            return true;
        }

        public boolean isRoleProtected() {
            return "roles".equals(access);
        }

        public boolean allowsAnyRole(Set<String> callerRoles) {
            if (callerRoles == null || callerRoles.isEmpty()) {
                return false;
            }
            for (String required : roles) {
                if (callerRoles.contains(required)) {
                    return true;
                }
            }
            return false;
        }
    }
}
