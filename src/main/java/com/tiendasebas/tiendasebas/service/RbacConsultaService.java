package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RbacConsultaService {

    private final JdbcTemplate jdbc;

    public RbacConsultaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listarPermisos() {
        TenantContext.getCurrentTenant();

        return jdbc.queryForList("""
            SELECT codigo, descripcion
            FROM public.rbac_permisos
            ORDER BY codigo
            """);
    }

    public List<Map<String, Object>> listarRoles() {
        Long tiendaId = TenantContext.getCurrentTenant();

        return jdbc.queryForList("""
            SELECT id, nombre, descripcion, activo, creado_en
            FROM public.rbac_roles
            WHERE tienda_id = ?
            ORDER BY nombre
            """, tiendaId);
    }

    public List<Map<String, Object>> permisosDeRol(Long rolId) {
        Long tiendaId = TenantContext.getCurrentTenant();

        return jdbc.queryForList("""
            SELECT p.codigo, p.descripcion
            FROM public.rbac_roles r
            JOIN public.rbac_roles_permisos rp
              ON rp.tienda_id = r.tienda_id
             AND rp.rol_id = r.id
            JOIN public.rbac_permisos p
              ON p.codigo = rp.permiso_codigo
            WHERE r.tienda_id = ?
              AND r.id = ?
              AND r.activo = TRUE
            ORDER BY p.codigo
            """, tiendaId, rolId);
    }
}