package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class RbacGestionService {

    private final JdbcTemplate jdbc;

    public RbacGestionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> crearRol(
            String nombre,
            String descripcion,
            List<String> permisos
    ) {
        Long tiendaId = TenantContext.getCurrentTenant();

        if (nombre == null || nombre.isBlank()
                || nombre.trim().length() > 80) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nombre de rol invalido"
            );
        }

        if (descripcion != null && descripcion.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Descripcion demasiado larga"
            );
        }

        if (permisos == null || permisos.size() > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lista de permisos invalida"
            );
        }

        List<String> codigos = permisos.stream()
                .map(c -> c == null ? "" : c.trim())
                .distinct()
                .toList();

        for (String codigo : codigos) {
            Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM public.rbac_permisos
                    WHERE codigo = ?
                )
                """, Boolean.class, codigo);

            if (!Boolean.TRUE.equals(existe)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Permiso desconocido: " + codigo
                );
            }
        }

        Long rolId;

        try {
            rolId = jdbc.queryForObject("""
                INSERT INTO public.rbac_roles
                    (tienda_id, nombre, descripcion)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                tiendaId,
                nombre.trim(),
                descripcion
            );
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un rol con ese nombre"
            );
        }

        for (String codigo : codigos) {
            jdbc.update("""
                INSERT INTO public.rbac_roles_permisos
                    (tienda_id, rol_id, permiso_codigo)
                VALUES (?, ?, ?)
                """,
                tiendaId,
                rolId,
                codigo
            );
        }

        return Map.of(
                "id", rolId,
                "nombre", nombre.trim(),
                "permisos", codigos,
                "activo", true
        );
    }
}