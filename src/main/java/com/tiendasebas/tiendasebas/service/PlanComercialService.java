package com.tiendasebas.tiendasebas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Selección persistente. No crea tablas ni modifica contratos históricos. */
@Service
public class PlanComercialService {
    public static final String CLAVE = "plan_comercial_principal";
    private final JdbcTemplate jdbc;
    private final SaasActividadService actividad;
    @Value("${app.saas.plan-comercial-id:0}")
    private long planComercialId;

    public PlanComercialService(JdbcTemplate jdbc, SaasActividadService actividad) {
        this.jdbc = jdbc;
        this.actividad = actividad;
    }
    public record Seleccion(Long planId, String origen, String motivo) {}

    /** Catálogo: escritores exclusivos; altas con lectura compartida antes del bloqueo de boutique. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquearCatalogo(boolean escritura) {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        String funcion = escritura ? "pg_advisory_xact_lock" : "pg_advisory_xact_lock_shared";
        jdbc.queryForObject("SELECT " + funcion + "(1397047873, 1)", (rs, fila) -> 0);
    }
    @Transactional(readOnly = true)
    public Seleccion consultar() {
        bloquearCatalogo(false);
        return resolver();
    }
    @Transactional(readOnly = true)
    public Long planComercial() { return consultar().planId(); }

    private Seleccion resolver() {
        List<String> valores = jdbc.queryForList(
                "SELECT valor FROM public.saas_configuracion WHERE clave = ?", String.class, CLAVE);
        if (!valores.isEmpty()) {
            try {
                long id = Long.parseLong(valores.getFirst());
                if (id <= 0) throw new NumberFormatException();
                return activo(id) ? new Seleccion(id, "CONFIGURACION", "SELECCION_EXPLICITA")
                        : new Seleccion(null, "CONFIGURACION", "PLAN_NO_ACTIVO_O_INEXISTENTE");
            } catch (NumberFormatException ex) {
                return new Seleccion(null, "CONFIGURACION", "CONFIGURACION_INVALIDA");
            }
        }
        // Compatibilidad: la selección persistida prevalece sobre la propiedad anterior.
        List<Long> planes = jdbc.queryForList("""
                SELECT id FROM public.planes_saas WHERE activo = TRUE AND (? = 0 OR id = ?)
                ORDER BY id LIMIT 2
                """, Long.class, planComercialId, planComercialId);
        if (planes.size() == 1) return new Seleccion(planes.getFirst(),
                planComercialId == 0 ? "CATALOGO" : "PROPIEDAD", "SELECCION_COMPATIBLE");
        return new Seleccion(null, planComercialId == 0 ? "CATALOGO" : "PROPIEDAD",
                planes.size() > 1 ? "MULTIPLES_PLANES_ACTIVOS" : "SIN_PLAN_ACTIVO_CONFIGURADO");
    }
    @Transactional
    public Seleccion seleccionar(Long id) {
        validarId(id);
        bloquearCatalogo(true);
        List<Boolean> estado = jdbc.queryForList(
                "SELECT activo FROM public.planes_saas WHERE id = ? FOR UPDATE", Boolean.class, id);
        if (estado.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El plan no existe");
        if (!Boolean.TRUE.equals(estado.getFirst()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El plan principal debe estar activo");
        List<String> anterior = jdbc.queryForList(
                "SELECT valor FROM public.saas_configuracion WHERE clave = ?", String.class, CLAVE);
        String valor = id.toString();
        if (anterior.isEmpty() || !valor.equals(anterior.getFirst())) {
            jdbc.update("""
                INSERT INTO public.saas_configuracion(clave, valor, actualizado_en) VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor, actualizado_en = CURRENT_TIMESTAMP
                """, CLAVE, valor);
            actividad.registrar("SELECCIONAR", "PLAN_COMERCIAL", id,
                    "Se seleccionó el plan comercial principal " + id);
        }
        return new Seleccion(id, "CONFIGURACION", "SELECCION_EXPLICITA");
    }
    /** El llamador ya mantiene el bloqueo exclusivo del catálogo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void comprobarDesactivacion(Long id) {
        Seleccion principal = resolver();
        if (id.equals(principal.planId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selecciona otro principal antes de desactivar este plan");
        Long activos = jdbc.queryForObject("SELECT COUNT(*) FROM public.planes_saas WHERE activo = TRUE", Long.class);
        if (activos == null || activos <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede desactivar el último plan activo");
    }
    private boolean activo(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM public.planes_saas WHERE id = ? AND activo = TRUE)", Boolean.class, id));
    }
    private void validarId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un plan válido");
    }
}
