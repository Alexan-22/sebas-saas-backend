
package com.tiendasebas.tiendasebas.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/actividad")
@RequiredArgsConstructor
public class SuperAdminActividadController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public List<Map<String, Object>> listar(
            @RequestParam(defaultValue = "100") int limite) {

        int cantidad = Math.max(1, Math.min(limite, 500));

        return jdbc.queryForList("""
            SELECT id, usuario, accion, entidad, entidad_id, descripcion,
                   to_char(creado_en AT TIME ZONE 'America/Lima',
                           'YYYY-MM-DD HH24:MI:SS') AS creado_en
            FROM public.saas_actividad
            ORDER BY id DESC
            LIMIT ?
            """, cantidad);
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        return jdbc.queryForMap("""
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (
                    WHERE (creado_en AT TIME ZONE 'America/Lima')::date =
                          (NOW() AT TIME ZONE 'America/Lima')::date
                ) AS hoy,
                COUNT(*) FILTER (
                    WHERE accion = 'CREAR'
                ) AS creaciones,
                COUNT(*) FILTER (
                    WHERE accion = 'ACTUALIZAR'
                ) AS actualizaciones
            FROM public.saas_actividad
            """);
    }
}
