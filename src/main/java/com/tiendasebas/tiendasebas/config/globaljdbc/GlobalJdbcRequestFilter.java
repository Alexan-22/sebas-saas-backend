package com.tiendasebas.tiendasebas.config.globaljdbc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limita el modo experimental a contratos auditados; no reemplaza JWT/RBAC. */
public final class GlobalJdbcRequestFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH = Set.of(
            "/api/auth/superadmin/login", "/api/auth/superadmin/password/forgot",
            "/api/auth/superadmin/password/reset", "/api/superadmin/auth/logout",
            "/api/superadmin/auth/password/change");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        GlobalRequestScope.clear();
        try {
            String path = request.getServletPath();
            String method = request.getMethod();
            boolean global = path.equals("/api/superadmin") || path.startsWith("/api/superadmin/")
                    || path.startsWith("/api/auth/superadmin/");
            if (global) {
                response.setHeader("Cache-Control", "no-store");
                if ("OPTIONS".equals(method)) {
                    chain.doFilter(request, response);
                    return;
                }
                if ("POST".equals(method) && AUTH.contains(path)) {
                    GlobalRequestScope.set(GlobalRequestScope.Mode.AUTHENTICATION);
                } else if (permittedOperation(method, path)) {
                    GlobalRequestScope.prepareOperations();
                } else {
                    response.sendError(403, "Operacion global no habilitada en este subconjunto");
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            GlobalRequestScope.clear();
        }
    }

    private static boolean permittedOperation(String method, String path) {
        return switch (method) {
            case "GET" -> path.equals("/api/superadmin/me")
                    || path.matches("/api/superadmin/tiendas")
                    || path.matches("/api/superadmin/propietarios(?:/tiendas/[1-9][0-9]*|/candidatos)?")
                    || path.matches("/api/superadmin/planes(?:/[1-9][0-9]*)?")
                    || path.matches("/api/superadmin/suscripciones(?:/tienda/[1-9][0-9]*|/plan-comercial)?");
            case "POST" -> Set.of("/api/superadmin/tiendas", "/api/superadmin/propietarios",
                    "/api/superadmin/planes", "/api/superadmin/suscripciones").contains(path);
            case "PUT" -> path.matches("/api/superadmin/tiendas/[1-9][0-9]*(?:/activar|/suspender|/pendiente)?")
                    || path.matches("/api/superadmin/propietarios/tiendas/[1-9][0-9]*")
                    || path.matches("/api/superadmin/planes/[1-9][0-9]*(?:/activar|/desactivar)?")
                    || path.equals("/api/superadmin/suscripciones/plan-comercial");
            default -> false;
        };
    }
}
