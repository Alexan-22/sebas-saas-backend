package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("rbacAutorizacionService")
public class RbacAutorizacionService {

    private final JdbcTemplate jdbc;

    public RbacAutorizacionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tienePermiso(
            Authentication authentication,
            String permiso
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || permiso == null
                || permiso.isBlank()) {
            return false;
        }

        Long tiendaId = TenantContext.getCurrentTenant();
        String correo = authentication.getName();

        Boolean autorizado = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM public.usuarios u
                JOIN public.roles base
                  ON base.id_rol = u.id_rol
                JOIN public.rbac_usuarios_roles ur
                  ON ur.usuario_id = u.id_usuario
                 AND ur.tienda_id = u.tienda_id
                JOIN public.rbac_roles r
                  ON r.id = ur.rol_id
                 AND r.tienda_id = ur.tienda_id
                JOIN public.rbac_roles_permisos rp
                  ON rp.rol_id = r.id
                 AND rp.tienda_id = r.tienda_id
                WHERE u.correo = ?
                  AND u.tienda_id = ?
                  AND u.estado = TRUE
                  AND base.nombre_rol = 'EMPLEADO'
                  AND r.activo = TRUE
                  AND rp.permiso_codigo = ?
            )
            """,
            Boolean.class,
            correo,
            tiendaId,
            permiso
        );

        return Boolean.TRUE.equals(autorizado);
    }

    public List<String> permisosActuales(
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            return List.of();
        }

        Long tiendaId = TenantContext.getCurrentTenant();

        return jdbc.queryForList("""
            SELECT DISTINCT rp.permiso_codigo
            FROM public.usuarios u
            JOIN public.roles base
              ON base.id_rol = u.id_rol
            JOIN public.rbac_usuarios_roles ur
              ON ur.usuario_id = u.id_usuario
             AND ur.tienda_id = u.tienda_id
            JOIN public.rbac_roles r
              ON r.id = ur.rol_id
             AND r.tienda_id = ur.tienda_id
            JOIN public.rbac_roles_permisos rp
              ON rp.rol_id = r.id
             AND rp.tienda_id = r.tienda_id
            WHERE u.correo = ?
              AND u.tienda_id = ?
              AND u.estado = TRUE
              AND base.nombre_rol = 'EMPLEADO'
              AND r.activo = TRUE
            ORDER BY rp.permiso_codigo
            """,
            String.class,
            authentication.getName(),
            tiendaId
        );
    }
}