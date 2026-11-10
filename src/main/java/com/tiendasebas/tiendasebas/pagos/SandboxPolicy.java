package com.tiendasebas.tiendasebas.pagos;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Fail closed: profile, explicit activation and authenticated commercial administrator. */
public final class SandboxPolicy {
    private final boolean enabled;
    public SandboxPolicy(Environment environment) {
        Set<String> profiles = Arrays.stream(environment.getActiveProfiles()).collect(Collectors.toSet());
        Set<String> allowed = Set.of("payments-sandbox", "local", "test", "cod-http-isolated");
        long environments = profiles.stream().filter(p -> !p.equals("payments-sandbox")).count();
        enabled = "true".equals(environment.getProperty("payments.sandbox.enabled"))
                && profiles.contains("payments-sandbox") && environments == 1
                && allowed.containsAll(profiles);
    }
    private SandboxPolicy() { enabled = false; }
    public static SandboxPolicy disabled() { return new SandboxPolicy(); }
    public void requireAuthorized() {
        if (!enabled) throw new AccessDeniedException("Sandbox no autorizado en este entorno");
        TenantContext.getCurrentTenant();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))
                || authentication.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Sandbox requiere ADMIN de tienda autenticado");
        }
    }
}
