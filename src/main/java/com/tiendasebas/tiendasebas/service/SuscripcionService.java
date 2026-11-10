
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.dto.CrearSuscripcionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class SuscripcionService {

    private static final ZoneId ZONA =
            ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;

    public SuscripcionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String CONSULTA = """
        SELECT s.id,
               s.tienda_id,
               t.nombre AS tienda,
               s.plan_id,
               p.nombre AS plan,
               s.estado,
               s.fecha_inicio,
               s.fecha_fin,
               s.precio_contratado,
               s.moneda
        FROM public.suscripciones_saas s
        JOIN public.tiendas t ON t.id = s.tienda_id
        JOIN public.planes_saas p ON p.id = s.plan_id
        """;

    public List<Map<String, Object>> listar() {
        return jdbc.queryForList(
                CONSULTA + " ORDER BY s.id DESC"
        );
    }

    public List<Map<String, Object>> listarPorTienda(
            Long tiendaId
    ) {
        return jdbc.queryForList(
                CONSULTA +
                        " WHERE s.tienda_id = ? ORDER BY s.id DESC",
                tiendaId
        );
    }

    @Transactional
    public Map<String, Object> crear(
            CrearSuscripcionRequest request
    ) {
        if (request == null ||
                request.tiendaId() == null ||
                request.planId() == null ||
                request.fechaInicio() == null ||
                request.tiendaId() <= 0 ||
                request.planId() <= 0) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Tienda, plan y fecha son obligatorios");
        }

        LocalDate hoy = LocalDate.now(ZONA);

        if (request.fechaInicio().isBefore(hoy)) {
            throw error(HttpStatus.BAD_REQUEST,
                    "No se permite crear una suscripcion retroactiva");
        }

        // Bloquear la tienda durante la transaccion.
        // Serializa las asignaciones concurrentes a esa tienda.
        List<Long> tiendas = jdbc.queryForList(
                """
                SELECT id
                FROM public.tiendas
                WHERE id = ?
                FOR UPDATE
                """,
                Long.class,
                request.tiendaId()
        );

        if (tiendas.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND,
                    "La tienda no existe");
        }

        List<Map<String, Object>> planes =
                jdbc.queryForList("""
                    SELECT id, precio_mensual,
                           duracion_meses, activo
                    FROM public.planes_saas
                    WHERE id = ?
                    """, request.planId());

        if (planes.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND,
                    "El plan no existe");
        }

        Map<String, Object> plan = planes.get(0);

        if (!Boolean.TRUE.equals(plan.get("activo"))) {
            throw error(HttpStatus.CONFLICT,
                    "El plan esta desactivado");
        }

        int meses = ((Number)
                plan.get("duracion_meses")).intValue();

        LocalDate inicio = request.fechaInicio();
        LocalDate fin = inicio.plusMonths(meses);

        if (!fin.isAfter(inicio)) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Periodo de suscripcion invalido");
        }

        // Las fechas representan intervalos [inicio, fin).
        Boolean solapamiento = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM public.suscripciones_saas
                WHERE tienda_id = ?
                  AND estado IN ('ACTIVA', 'PENDIENTE')
                  AND fecha_inicio < ?
                  AND fecha_fin > ?
            )
            """, Boolean.class,
                request.tiendaId(), fin, inicio
        );

        if (Boolean.TRUE.equals(solapamiento)) {
            throw error(HttpStatus.CONFLICT,
                    "La tienda ya tiene una suscripcion " +
                            "que coincide con esas fechas");
        }

        BigDecimal precio = request.precioContratado();

        if (precio == null) {
            precio = (BigDecimal)
                    plan.get("precio_mensual");
        }

        if (precio == null ||
                precio.signum() < 0 ||
                precio.scale() > 2 ||
                precio.compareTo(
                        new BigDecimal("9999999999.99")) > 0) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Precio contratado invalido");
        }

        String estado = inicio.isAfter(hoy)
                ? "PENDIENTE" : "ACTIVA";

        Long id = jdbc.queryForObject("""
            INSERT INTO public.suscripciones_saas (
                tienda_id,
                plan_id,
                estado,
                fecha_inicio,
                fecha_fin,
                precio_contratado,
                moneda,
                creado_en,
                actualizado_en
            )
            VALUES (?, ?, ?, ?, ?, ?, 'PEN',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
            """, Long.class,
                request.tiendaId(),
                request.planId(),
                estado,
                inicio,
                fin,
                precio
        );

        return jdbc.queryForMap(
                CONSULTA + " WHERE s.id = ?", id
        );
    }

    private ResponseStatusException error(
            HttpStatus status,
            String mensaje
    ) {
        return new ResponseStatusException(
                status, mensaje
        );
    }
}
