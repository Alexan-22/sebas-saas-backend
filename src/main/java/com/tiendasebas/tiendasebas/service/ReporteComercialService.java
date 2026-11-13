package com.tiendasebas.tiendasebas.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Reportes de contratos: no existe aquí un libro de pagos cobrados. */
@Service
@Transactional(readOnly = true)
public class ReporteComercialService {
    private final JdbcTemplate jdbc;
    public ReporteComercialService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private record Consulta(String cte, List<Object> argumentos, Long tienda, Long plan, String moneda, LocalDate referencia) {}
    private static final Set<String> CLAVES = Set.of("tiendaId", "planId", "moneda", "estado", "desde", "hasta", "fechaReferencia");

    private Consulta preparar(Map<String, String> filtros) {
        if (!CLAVES.containsAll(filtros.keySet())) throw invalido("Filtro comercial no reconocido");
        Long tienda = identificador(filtros.get("tiendaId"));
        Long plan = identificador(filtros.get("planId"));
        String moneda = filtros.getOrDefault("moneda", "PEN").trim().toUpperCase(Locale.ROOT);
        if (!moneda.matches("[A-Z]{3}")) throw invalido("Moneda no válida");
        LocalDate referencia = fecha(filtros.get("fechaReferencia"));
        if (referencia == null) referencia = LocalDate.now(ZoneId.of("America/Lima"));
        LocalDate desde = fecha(filtros.get("desde")), hasta = fecha(filtros.get("hasta"));
        if (desde != null && hasta != null && desde.isAfter(hasta)) throw invalido("El rango de fechas no es válido");
        String estado = filtros.get("estado");
        if (estado != null && !Set.of("ACTIVA", "PENDIENTE", "VENCIDA", "CANCELADA").contains(estado))
            throw invalido("Estado de suscripción no válido");
        var args = new ArrayList<Object>();
        args.add(referencia); args.add(referencia); args.add(moneda);
        var sql = new StringBuilder("""
            WITH base AS (
              SELECT s.*, t.nombre AS tienda, p.nombre AS plan,
                CASE WHEN s.estado = 'CANCELADA' THEN 'CANCELADA'
                     WHEN s.fecha_fin <= ? THEN 'VENCIDA'
                     WHEN s.fecha_inicio > ? THEN 'PENDIENTE' ELSE 'ACTIVA' END AS estado_efectivo
              FROM public.suscripciones_saas s
              JOIN public.tiendas t ON t.id = s.tienda_id
              JOIN public.planes_saas p ON p.id = s.plan_id
              WHERE s.moneda = ?
            """);
        if (tienda != null) { sql.append(" AND s.tienda_id = ?"); args.add(tienda); }
        if (plan != null) { sql.append(" AND s.plan_id = ?"); args.add(plan); }
        // Se incluyen contratos cuyo periodo intersecta el rango de días inclusivo.
        if (desde != null) { sql.append(" AND s.fecha_fin > ?"); args.add(desde); }
        if (hasta != null) { sql.append(" AND s.fecha_inicio <= ?"); args.add(hasta); }
        sql.append("), filtradas AS (SELECT * FROM base");
        if (estado != null) { sql.append(" WHERE estado_efectivo = ?"); args.add(estado); }
        sql.append(") ");
        return new Consulta(sql.toString(), args, tienda, plan, moneda, referencia);
    }

    public Map<String, Object> resumen(Map<String, String> filtros) {
        Consulta c = preparar(filtros);
        var args = new ArrayList<>(c.argumentos());
        String tiendas = c.tienda() == null ? "" : " WHERE id = ?";
        // Subconsultas de boutiques con el mismo ámbito solicitado.
        String scope = c.tienda() == null ? "" : " AND id = ?";
        if (c.tienda() != null) { args.add(c.tienda()); args.add(c.tienda()); args.add(c.tienda()); args.add(c.tienda()); }
        args.add(c.moneda());
        String planes = c.plan() == null ? "" : " AND id = ?";
        if (c.plan() != null) args.add(c.plan());
        args.add(c.referencia().plusDays(15));
        Map<String,Object> resultado = jdbc.queryForMap(c.cte() + """
            SELECT
            (SELECT COUNT(*) FROM public.tiendas
            """ + tiendas + ") AS total_tiendas, " +
            "(SELECT COUNT(*) FROM public.tiendas WHERE estado = 'ACTIVA'" + scope + ") AS tiendas_activas, " +
            "(SELECT COUNT(*) FROM public.tiendas WHERE estado = 'PENDIENTE'" + scope + ") AS tiendas_pendientes, " +
            "(SELECT COUNT(*) FROM public.tiendas WHERE estado = 'SUSPENDIDA'" + scope + ") AS tiendas_suspendidas, " +
            "(SELECT COUNT(*) FROM public.planes_saas WHERE activo = TRUE AND moneda = ?" + planes + ") AS planes_activos, " + """
            COUNT(*) AS total_suscripciones,
            COUNT(*) FILTER (WHERE estado_efectivo = 'ACTIVA') AS suscripciones_activas,
            COUNT(*) FILTER (WHERE estado_efectivo = 'PENDIENTE') AS suscripciones_pendientes,
            COUNT(*) FILTER (WHERE estado_efectivo = 'CANCELADA') AS suscripciones_canceladas,
            COUNT(*) FILTER (WHERE estado_efectivo = 'VENCIDA') AS suscripciones_vencidas,
            COUNT(*) FILTER (WHERE estado_efectivo = 'ACTIVA' AND fecha_fin <= ?) AS proximas_a_vencer,
            COALESCE(SUM(precio_contratado) FILTER (WHERE estado_efectivo = 'ACTIVA'),0) AS mrr_contratado
            FROM filtradas
            """, args.toArray());
        resultado.put("importe_contratado", resultado.get("mrr_contratado"));
        resultado.put("moneda", c.moneda());
        resultado.put("fecha_referencia", c.referencia().toString());
        resultado.put("ingresos_cobrados_disponibles", false);
        return resultado;
    }

    public List<Map<String,Object>> porPlan(Map<String,String> filtros) {
        Consulta c = preparar(filtros);
        var args = new ArrayList<>(c.argumentos()); args.add(c.moneda());
        String plan = c.plan() == null ? "" : " AND p.id = ?";
        if (c.plan() != null) args.add(c.plan());
        return jdbc.queryForList(c.cte() + """
            SELECT p.id AS plan_id, p.nombre AS plan, ? AS moneda,
              COUNT(s.id) FILTER (WHERE s.estado_efectivo = 'ACTIVA') AS suscripciones_activas,
              COALESCE(SUM(s.precio_contratado) FILTER (WHERE s.estado_efectivo = 'ACTIVA'),0) AS mrr_contratado
            FROM public.planes_saas p LEFT JOIN filtradas s ON s.plan_id = p.id
            WHERE TRUE
            """ + plan + " GROUP BY p.id, p.nombre ORDER BY p.id", args.toArray());
    }

    public List<Map<String,Object>> suscripciones(Map<String,String> filtros) {
        Consulta c = preparar(filtros);
        return jdbc.queryForList(c.cte() + """
            SELECT id, tienda_id, tienda, plan_id, plan, estado, estado_efectivo,
                   fecha_inicio, fecha_fin, precio_contratado, moneda
            FROM filtradas ORDER BY fecha_fin, tienda_id, id
            """, c.argumentos().toArray());
    }

    private Long identificador(String valor) {
        if (valor == null) return null;
        try { long id = Long.parseLong(valor); if (id > 0) return id; }
        catch (NumberFormatException ignored) { }
        throw invalido("Identificador de filtro no válido");
    }
    private LocalDate fecha(String valor) {
        if (valor == null) return null;
        try {
            if (!valor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalido("Fecha no válida");
            LocalDate fecha = LocalDate.parse(valor);
            if (fecha.getYear() < 1 || fecha.getYear() > 9998) throw invalido("Fecha fuera de rango");
            return fecha;
        } catch (java.time.DateTimeException ex) { throw invalido("Fecha no válida"); }
    }
    private ResponseStatusException invalido(String mensaje) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje); }
}
