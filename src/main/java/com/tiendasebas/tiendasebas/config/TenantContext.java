package com.tiendasebas.tiendasebas.config;

/** El tenant se establece exclusivamente en JwtAuthenticationFilter. */
public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private TenantContext() {}
    public static void setCurrentTenant(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Tenant valido obligatorio");
        CURRENT.set(id);
    }
    public static Long getCurrentTenant() {
        Long id = CURRENT.get();
        if (id == null || id <= 0) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Tenant no resuelto");
        return id;
    }
    public static void clear() { CURRENT.remove(); }
}
