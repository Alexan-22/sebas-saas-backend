package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class RbacAsignacionService {

    private final JdbcTemplate jdbc;

    public RbacAsignacionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void asignar(Integer usuarioId, Long rolId) {
        Long tiendaId = TenantContext.getCurrentTenant();

        if (usuarioId == null || usuarioId <= 0
                || rolId == null || rolId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Identificadores invalidos");
        }

        Boolean empleado = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM public.usuarios u
                JOIN public.roles r ON r.id_rol = u.id_rol
                WHERE u.id_usuario = ?
                  AND u.tienda_id = ?
                  AND u.estado = TRUE
                  AND r.nombre_rol = 'EMPLEADO'
            )
            """, Boolean.class, usuarioId, tiendaId);

        if (!Boolean.TRUE.equals(empleado)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Empleado activo no encontrado en esta tienda");
        }

        Boolean rolValido = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM public.rbac_roles
                WHERE id = ?
                  AND tienda_id = ?
                  AND activo = TRUE
            )
            """, Boolean.class, rolId, tiendaId);

        if (!Boolean.TRUE.equals(rolValido)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Rol activo no encontrado en esta tienda");
        }

        int filas = jdbc.update("""
            INSERT INTO public.rbac_usuarios_roles
                (tienda_id, usuario_id, rol_id)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """, tiendaId, usuarioId, rolId);

        if (filas == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El empleado ya tiene asignado ese rol");
        }
    }

    public List<Map<String, Object>> rolesDeEmpleado(
            Integer usuarioId) {
        Long tiendaId = TenantContext.getCurrentTenant();

        return jdbc.queryForList("""
            SELECT r.id, r.nombre, r.descripcion, r.activo
            FROM public.rbac_usuarios_roles ur
            JOIN public.rbac_roles r
              ON r.id = ur.rol_id
             AND r.tienda_id = ur.tienda_id
            WHERE ur.tienda_id = ?
              AND ur.usuario_id = ?
            ORDER BY r.nombre
            """, tiendaId, usuarioId);
    }

    @Transactional
    public void revocar(Integer usuarioId, Long rolId) {
        Long tiendaId = TenantContext.getCurrentTenant();

        int filas = jdbc.update("""
            DELETE FROM public.rbac_usuarios_roles
            WHERE tienda_id = ?
              AND usuario_id = ?
              AND rol_id = ?
            """, tiendaId, usuarioId, rolId);

        if (filas == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Asignacion no encontrada");
        }
    }
}