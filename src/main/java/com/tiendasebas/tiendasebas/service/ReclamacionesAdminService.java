package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/** Bandeja administrativa: ninguna operacion acepta tiendaId del cliente. */
@Service
public class ReclamacionesAdminService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ConstanciaReclamacionesService constancias;

    public ReclamacionesAdminService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                    ConstanciaReclamacionesService constancias) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.constancias = constancias;
    }

    public record Resumen(long numeroHoja, OffsetDateTime recibidoEn, String tipo,
                          String consumidorNombre, String estado) {}
    public record Evento(OffsetDateTime creadoEn, String tipo, String contenido, String actor, String estado) {}
    public record Detalle(ConstanciaReclamacionesService.Hoja hoja, List<Evento> eventos) {}

    public List<Resumen> listar(int limite, long antesDe) {
        if (limite < 1 || limite > 100 || antesDe < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginacion invalida");
        long tenant = TenantContext.getCurrentTenant();
        return tx.execute(s -> {
            contexto(tenant);
            return jdbc.query("""
                    SELECT r.numero_hoja, r.recibido_en, r.tipo, r.consumidor_nombre,
                           COALESCE((SELECT e.estado FROM public.saas_reclamaciones_eventos e
                                     WHERE e.tienda_id = r.tienda_id AND e.reclamacion_id = r.id
                                       AND e.estado IS NOT NULL
                                     ORDER BY e.creado_en DESC, e.id DESC LIMIT 1), 'RECIBIDO') AS estado
                    FROM public.saas_reclamaciones r
                    WHERE r.tienda_id = ? AND (? = 0 OR r.numero_hoja < ?)
                    ORDER BY r.numero_hoja DESC LIMIT ?
                    """, (rs, i) -> new Resumen(
                    rs.getLong("numero_hoja"), rs.getObject("recibido_en", OffsetDateTime.class),
                    rs.getString("tipo"), rs.getString("consumidor_nombre"), rs.getString("estado")),
                    tenant, antesDe, antesDe, limite);
        });
    }

    public Detalle detalle(long numeroHoja) {
        if (numeroHoja <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hoja invalida");
        long tenant = TenantContext.getCurrentTenant();
        // La consulta de la hoja se realiza en su propia transaccion segura y validada.
        var hoja = constancias.obtener(numeroHoja);
        var eventos = tx.execute(s -> {
            contexto(tenant);
            return jdbc.query("""
                    SELECT e.creado_en, e.tipo, e.contenido, e.actor, e.estado
                    FROM public.saas_reclamaciones_eventos e
                    JOIN public.saas_reclamaciones r
                      ON r.id = e.reclamacion_id AND r.tienda_id = e.tienda_id
                    WHERE e.tienda_id = ? AND r.numero_hoja = ?
                    ORDER BY e.creado_en, e.id
                    """, (rs, i) -> new Evento(rs.getObject("creado_en", OffsetDateTime.class),
                    rs.getString("tipo"), rs.getString("contenido"),
                    rs.getString("actor"), rs.getString("estado")), tenant, numeroHoja);
        });
        return new Detalle(hoja, eventos);
    }

    /** Nota interna de seguimiento; no se presenta como respuesta notificada al consumidor. */
    public void agregarNota(long numeroHoja, String texto, String actor) {
        if (numeroHoja <= 0 || texto == null || texto.isBlank() || texto.length() > 4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nota invalida");
        if (actor == null || actor.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Administrador no identificado");
        long tenant = TenantContext.getCurrentTenant();
        tx.executeWithoutResult(s -> {
            contexto(tenant);
            int inserted = jdbc.update("""
                    INSERT INTO public.saas_reclamaciones_eventos
                    (tienda_id, reclamacion_id, tipo, contenido, actor, estado)
                    SELECT r.tienda_id, r.id, 'NOTA_INTERNA', ?, ?, NULL
                    FROM public.saas_reclamaciones r
                    WHERE r.tienda_id = ? AND r.numero_hoja = ?
                    """, texto.trim(), actor.substring(0, Math.min(actor.length(), 200)), tenant, numeroHoja);
            if (inserted != 1)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hoja no encontrada");
        });
    }

    private void contexto(long tenant) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, Long.toString(tenant));
    }
}
