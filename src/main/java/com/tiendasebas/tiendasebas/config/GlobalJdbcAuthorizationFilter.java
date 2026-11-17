package com.tiendasebas.tiendasebas.config.globaljdbc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Se ejecuta dentro de Spring Security, despues de la validacion JWT y de sesion. */
public final class GlobalJdbcAuthorizationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!GlobalRequestScope.operationsRequested()) {
            chain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.sendError(401, "Autenticacion global requerida");
            return;
        }
        if (auth.getAuthorities().stream().noneMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()))) {
            response.sendError(403, "Operacion global no autorizada");
            return;
        }
        GlobalRequestScope.authorizeOperations();
        try {
            chain.doFilter(request, response);
        } finally {
            GlobalRequestScope.prepareOperations();
        }
    }
}
