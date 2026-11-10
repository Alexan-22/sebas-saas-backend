
package com.tiendasebas.tiendasebas.security;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.config.TenantResolver;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// ============================================
// SEBAS COLLECTION SAAS V2
// FILTRO JWT GLOBAL Y MULTITENANT
// ============================================

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    // ========================================
    // DEPENDENCIAS
    // ========================================

    private final JwtService jwt;

    private final UsuarioRepository usuarios;

    private final TenantResolver resolver;

    // ========================================
    // CONSTRUCTOR
    // ========================================

    public JwtAuthenticationFilter(
            JwtService jwt,
            UsuarioRepository usuarios,
            TenantResolver resolver
    ) {
        this.jwt = jwt;
        this.usuarios = usuarios;
        this.resolver = resolver;
    }

    // ========================================
    // FILTRO PRINCIPAL
    // ========================================

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        try {

            String ruta = request.getServletPath();

            // =================================
            // SOLICITUDES CORS PREFLIGHT
            // =================================

            if ("OPTIONS".equalsIgnoreCase(
                    request.getMethod()
            )) {

                chain.doFilter(request, response);
                return;
            }

            // =================================
            // LOGIN GLOBAL SUPERADMIN
            // =================================

            // El endpoint realiza su propia
            // autenticacion con credenciales.

            if ("/api/auth/superadmin/login".equals(ruta)) {

                chain.doFilter(request, response);
                return;
            }

            // =================================
            // ENDPOINTS DEL SUPERADMIN
            // =================================

            // No dependen de ningun tenant.
            // Una tienda suspendida no debe
            // bloquear la administracion global.

            if (
                    "/api/superadmin".equals(ruta) ||
                            ruta.startsWith("/api/superadmin/")
            ) {

                autenticarSuperAdmin(
                        request,
                        response,
                        chain
                );

                return;
            }

            // =================================
            // RESTO DE ENDPOINTS POR TIENDA
            // =================================

            autenticarTienda(
                    request,
                    response,
                    chain
            );

        } finally {

            // Impedir que el contexto de una
            // solicitud permanezca en el hilo.

            SecurityContextHolder.clearContext();

            TenantContext.clear();
        }
    }

    // ========================================
    // AUTENTICACION SUPERADMIN GLOBAL
    // ========================================

    private void autenticarSuperAdmin(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        String token = obtenerBearer(request);

        if (token == null) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Autenticacion requerida"
            );

            return;
        }

        // ====================================
        // VERIFICAR JWT
        // ====================================

        Claims claims;

        try {

            claims = jwt.getClaims(token);

        } catch (
                JwtException |
                IllegalArgumentException ex
        ) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Token invalido o expirado"
            );

            return;
        }

        // ====================================
        // LEER IDENTIDAD DEL TOKEN
        // ====================================

        Number uid;
        Number tid;
        String rolToken;
        String correo;

        try {

            uid = claims.get("uid", Number.class);

            tid = claims.get("tid", Number.class);

            rolToken = claims.get(
                    "rol",
                    String.class
            );

            correo = claims.getSubject();

        } catch (RuntimeException ex) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Claims de token invalidos"
            );

            return;
        }

        Integer usuarioId = validarUsuarioId(uid);

        // El superadministrador debe:
        // - tener un ID valido
        // - tener rol SUPER_ADMIN
        // - no pertenecer a ninguna tienda
        // - presentar un correo en el JWT

        if (
                usuarioId == null ||
                        tid != null ||
                        !"SUPER_ADMIN".equals(rolToken) ||
                        correo == null ||
                        correo.isBlank()
        ) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Token no autorizado"
            );

            return;
        }

        // ====================================
        // CONSULTAR USUARIO REAL
        // ====================================

        // Esta consulta queda fuera del
        // catch del JWT. Si falla PostgreSQL,
        // no se ocultara como token invalido.

        Optional<Usuario> encontrado =
                usuarios.findByCorreoAndTiendaIdIsNull(
                        correo
                );

        if (encontrado.isEmpty()) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Usuario no encontrado"
            );

            return;
        }

        Usuario usuario = encontrado.get();

        // ====================================
        // VERIFICAR SUPERADMIN EN BD
        // ====================================

        if (
                !Objects.equals(
                        usuario.getIdUsuario(),
                        usuarioId
                ) ||
                        !Boolean.TRUE.equals(
                                usuario.getEstado()
                        ) ||
                        usuario.getTiendaId() != null ||
                        usuario.getRol() == null ||
                        !"SUPER_ADMIN".equals(
                                usuario.getRol().getNombreRol()
                        )
        ) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Usuario no autorizado"
            );

            return;
        }

        // ====================================
        // AUTENTICACION AUTORIZADA
        // ====================================

        establecerAutenticacion(usuario);

        chain.doFilter(request, response);
    }

    // ========================================
    // AUTENTICACION MULTITENANT
    // ========================================

    private void autenticarTienda(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        // ====================================
        // RESOLVER TENANT ACTIVO
        // ====================================

        // TenantResolver verifica:
        // 1. Dominio o subdominio.
        // 2. Existencia de la tienda.
        // 3. Estado ACTIVA.
        //
        // Si esta PENDIENTE o SUSPENDIDA,
        // devuelve Optional.empty().

        Long tiendaId = resolver
                .resolver(request)
                .orElse(null);

        if (tiendaId == null) {

            response.sendError(
                    HttpServletResponse.SC_NOT_FOUND,
                    "Tienda no encontrada o no disponible"
            );

            return;
        }

        // ====================================
        // ESTABLECER CONTEXTO MULTITENANT
        // ====================================

        TenantContext.setCurrentTenant(
                tiendaId
        );

        // ====================================
        // LEER HEADER DE AUTORIZACION
        // ====================================

        String authorization =
                request.getHeader("Authorization");

        String token = obtenerBearer(request);

        // Si el cliente envia Authorization
        // pero no tiene formato Bearer valido,
        // rechazamos la solicitud.

        if (
                authorization != null &&
                        !authorization.isBlank() &&
                        token == null
        ) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Cabecera Authorization invalida"
            );

            return;
        }

        // Sin token, dejamos continuar.
        // Spring Security decide si la ruta
        // permite acceso publico o requiere
        // autenticacion.

        if (token == null) {

            chain.doFilter(request, response);
            return;
        }

        // ====================================
        // VERIFICAR JWT
        // ====================================

        Claims claims;

        try {

            claims = jwt.getClaims(token);

        } catch (
                JwtException |
                IllegalArgumentException ex
        ) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Token invalido o expirado"
            );

            return;
        }

        // ====================================
        // LEER CLAIMS
        // ====================================

        Number tokenTenant;
        Number uid;
        String rolToken;
        String correoToken;

        try {

            tokenTenant = claims.get(
                    "tid",
                    Number.class
            );

            uid = claims.get(
                    "uid",
                    Number.class
            );

            rolToken = claims.get(
                    "rol",
                    String.class
            );

            correoToken = claims.getSubject();

        } catch (RuntimeException ex) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Claims de token invalidos"
            );

            return;
        }

        Integer usuarioId = validarUsuarioId(uid);

        // ====================================
        // AISLAMIENTO POR TIENDA
        // ====================================

        // Un token de la tienda 7 nunca
        // debe autenticar usuarios de la 8.

        if (
                usuarioId == null ||
                        tokenTenant == null ||
                        tokenTenant.longValue() != tiendaId.longValue() ||
                        correoToken == null ||
                        correoToken.isBlank() ||
                        rolToken == null ||
                        "SUPER_ADMIN".equals(rolToken)
        ) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Token no autorizado para esta tienda"
            );

            return;
        }

        // ====================================
        // CONSULTAR USUARIO POR ID Y TIENDA
        // ====================================

        Optional<Usuario> encontrado =
                usuarios.findByIdUsuarioAndTiendaId(
                        usuarioId,
                        tiendaId
                );

        if (encontrado.isEmpty()) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Usuario no encontrado"
            );

            return;
        }

        Usuario usuario = encontrado.get();

        // ====================================
        // VERIFICAR USUARIO Y ROL
        // ====================================

        if (
                !Boolean.TRUE.equals(
                        usuario.getEstado()
                ) ||
                        usuario.getCorreo() == null ||
                        !usuario.getCorreo().equalsIgnoreCase(
                                correoToken
                        ) ||
                        usuario.getRol() == null ||
                        !rolToken.equals(
                                usuario.getRol().getNombreRol()
                        )
        ) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Usuario no autorizado"
            );

            return;
        }

        // ====================================
        // AUTENTICACION AUTORIZADA
        // ====================================

        establecerAutenticacion(usuario);

        chain.doFilter(request, response);
    }

    // ========================================
    // EXTRAER TOKEN BEARER
    // ========================================

    private String obtenerBearer(
            HttpServletRequest request
    ) {

        String authorization =
                request.getHeader("Authorization");

        if (
                authorization == null ||
                        !authorization.regionMatches(
                                true,
                                0,
                                "Bearer ",
                                0,
                                7
                        )
        ) {

            return null;
        }

        String token =
                authorization.substring(7).trim();

        return token.isEmpty() ? null : token;
    }

    // ========================================
    // VALIDAR ID NUMERICO DEL TOKEN
    // ========================================

    private Integer validarUsuarioId(
            Number uid
    ) {

        if (uid == null) {
            return null;
        }

        long valor = uid.longValue();

        if (
                valor <= 0 ||
                        valor > Integer.MAX_VALUE
        ) {
            return null;
        }

        // Evitar aceptar valores fraccionarios
        // como identificadores de usuarios.

        if (uid.doubleValue() != (double) valor) {
            return null;
        }

        return (int) valor;
    }

    // ========================================
    // REGISTRAR AUTENTICACION EN SPRING
    // ========================================

    private void establecerAutenticacion(
            Usuario usuario
    ) {

        String autoridad =
                "ROLE_" +
                        usuario.getRol().getNombreRol();

        UsernamePasswordAuthenticationToken autenticacion =
                new UsernamePasswordAuthenticationToken(
                        usuario.getCorreo(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        autoridad
                                )
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(autenticacion);
    }
}
