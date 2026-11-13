
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.dto.CrearSuscripcionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.RoundingMode;
import java.time.DateTimeException;
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

    private final PlanComercialService comercial;
    private final SaasActividadService actividad;

    public SuscripcionService(JdbcTemplate jdbc, PlanComercialService comercial, SaasActividadService actividad) {
        this.jdbc = jdbc;
        this.comercial = comercial;
        this.actividad = actividad;
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
        if (tiendaId == null || tiendaId <= 0) throw error(HttpStatus.BAD_REQUEST, "Boutique no válida");
        return jdbc.queryForList(
                CONSULTA +
                        " WHERE s.tienda_id = ? ORDER BY s.id DESC",
                tiendaId
        );
    }

    public Long planComercial() { return comercial.planComercial(); }

    public PlanComercialService.Seleccion consultarPlanComercial() { return comercial.consultar(); }

    public PlanComercialService.Seleccion seleccionarPlanComercial(Long id) { return comercial.seleccionar(id); }

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

        comercial.bloquearCatalogo(false);
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
                    SELECT id, precio_mensual, moneda,
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

        Long principal = planComercial();
        if (principal == null) {
            throw error(HttpStatus.CONFLICT, "Configura el plan comercial principal antes de crear suscripciones");
        }
        if (!principal.equals(request.planId())) {
            throw error(HttpStatus.CONFLICT, "Selecciona el plan comercial principal");
        }

        int meses = ((Number)
                plan.get("duracion_meses")).intValue();

        LocalDate inicio = request.fechaInicio();
        if (meses < 1 || meses > 36) throw error(HttpStatus.CONFLICT, "El plan tiene una duración no válida");
        LocalDate fin;
        try { fin = inicio.plusMonths(meses); }
        catch (DateTimeException ex) { throw error(HttpStatus.BAD_REQUEST, "Fecha de suscripción fuera de rango"); }
        if (inicio.getYear() < 1 || fin.getYear() > 9999) throw error(HttpStatus.BAD_REQUEST, "Fecha de suscripción fuera de rango");

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
                  AND estado <> 'CANCELADA'
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

        try { precio = precio.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw error(HttpStatus.BAD_REQUEST, "Precio contratado inválido"); }
        String moneda = String.valueOf(plan.get("moneda")).trim();
        if (!moneda.matches("[A-Z]{3}")) throw error(HttpStatus.CONFLICT, "Moneda del plan no válida");

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
            VALUES (?, ?, ?, ?, ?, ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
            """, Long.class,
                request.tiendaId(),
                request.planId(),
                estado,
                inicio,
                fin,
                precio, moneda
        );

        actividad.registrar("CREAR", "SUSCRIPCION", id,
                "Se registró la suscripción " + id + " para la boutique " + request.tiendaId());
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
