
package com.tiendasebas.tiendasebas.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/reportes/saas")
@RequiredArgsConstructor
public class SuperAdminReporteSaasController {

    private final JdbcTemplate jdbc;

    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM public.tiendas) AS total_tiendas,
                (SELECT COUNT(*) FROM public.planes_saas WHERE activo = TRUE) AS planes_activos,
                COUNT(*) AS total_suscripciones,
                COUNT(*) FILTER (
                    WHERE estado <> 'CANCELADA'
                      AND fecha_inicio <= CURRENT_DATE
                      AND fecha_fin > CURRENT_DATE
                ) AS suscripciones_activas,
                COUNT(*) FILTER (
                    WHERE estado = 'PENDIENTE'
                      AND fecha_inicio > CURRENT_DATE
                ) AS suscripciones_pendientes,
                COUNT(*) FILTER (
                    WHERE estado = 'CANCELADA'
                ) AS suscripciones_canceladas,
                COUNT(*) FILTER (
                    WHERE estado <> 'CANCELADA'
                      AND fecha_fin <= CURRENT_DATE
                ) AS suscripciones_vencidas,
                COUNT(*) FILTER (
                    WHERE estado <> 'CANCELADA'
                      AND fecha_inicio <= CURRENT_DATE
                      AND fecha_fin > CURRENT_DATE
                      AND fecha_fin <= CURRENT_DATE + 15
                ) AS proximas_a_vencer,
                COALESCE(SUM(precio_contratado) FILTER (
                    WHERE estado <> 'CANCELADA'
                      AND fecha_inicio <= CURRENT_DATE
                      AND fecha_fin > CURRENT_DATE
                ), 0) AS mrr_contratado
            FROM public.suscripciones_saas
            """;

        return jdbc.queryForMap(sql);
    }

    @GetMapping("/planes")
    public List<Map<String, Object>> porPlan() {
        String sql = """
            SELECT
                p.id AS plan_id,
                p.nombre AS plan,
                COUNT(s.id) FILTER (
                    WHERE s.estado <> 'CANCELADA'
                      AND s.fecha_inicio <= CURRENT_DATE
                      AND s.fecha_fin > CURRENT_DATE
                ) AS suscripciones_activas,
                COALESCE(SUM(s.precio_contratado) FILTER (
                    WHERE s.estado <> 'CANCELADA'
                      AND s.fecha_inicio <= CURRENT_DATE
                      AND s.fecha_fin > CURRENT_DATE
                ), 0) AS mrr_contratado
            FROM public.planes_saas p
            LEFT JOIN public.suscripciones_saas s
                ON s.plan_id = p.id
            GROUP BY p.id, p.nombre
            ORDER BY p.id
            """;

        return jdbc.queryForList(sql);
    }

    @GetMapping("/suscripciones")
    public List<Map<String, Object>> suscripciones() {
        String sql = """
            SELECT
                s.id,
                t.nombre AS tienda,
                p.nombre AS plan,
                s.estado,
                s.fecha_inicio,
                s.fecha_fin,
                s.precio_contratado,
                s.moneda
            FROM public.suscripciones_saas s
            JOIN public.tiendas t ON t.id = s.tienda_id
            JOIN public.planes_saas p ON p.id = s.plan_id
            ORDER BY s.fecha_fin, t.nombre
            """;

        return jdbc.queryForList(sql);
    }
}
