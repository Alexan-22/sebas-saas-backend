package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Guarda una respuesta formal inmutable y, si existe correo de consumidor válido,
 * una notificación PENDIENTE dentro de la misma transacción.
 * NO declara correo enviado: requiere un transportador SMTP/outbox separado y probado.
 */
@Service
public class RespuestaFormalReclamacionesService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public RespuestaFormalReclamacionesService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
    }

    public record Resultado(long numeroHoja, boolean correoPendiente) {}

    public Resultado responder(long numeroHoja, String texto, String actor) {
        if (numeroHoja <= 0 || texto == null || texto.isBlank() || texto.length() > 12000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Respuesta formal inválida");
        }
        if (actor == null || actor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Administrador no identificado");
        }
        long tenant = TenantContext.getCurrentTenant();
        return tx.execute(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenant));
            List<Destinatario> encontrados = jdbc.query("""
                SELECT r.id, r.correo FROM public.saas_reclamaciones r
                WHERE r.tienda_id = ? AND r.numero_hoja = ?
                FOR UPDATE
                """, (rs, i) -> new Destinatario(rs.getLong(1), rs.getString(2)), tenant, numeroHoja);
            if (encontrados.size() != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hoja no encontrada");
            }
            Long previas = jdbc.queryForObject("""
                SELECT count(*) FROM public.saas_reclamaciones_eventos
                WHERE tienda_id = ? AND reclamacion_id = ? AND tipo = 'RESPUESTA'
                """, Long.class, tenant, encontrados.get(0).reclamacionId());
            if (previas != null && previas > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La hoja ya tiene una respuesta formal");
            }
            Long eventoId = jdbc.queryForObject("""
                INSERT INTO public.saas_reclamaciones_eventos
                  (tienda_id,reclamacion_id,tipo,contenido,actor,estado)
                VALUES (?,?,'RESPUESTA',?,?,'RESPONDIDO') RETURNING id
                """, Long.class, tenant, encontrados.get(0).reclamacionId(), texto.trim(), actor.substring(0, Math.min(200,actor.length())));
            if (eventoId == null) throw new IllegalStateException("No se creó evento de respuesta");
            String correo = encontrados.get(0).correo();
            boolean pendiente = correo != null && correo.length() <= 254 && EMAIL.matcher(correo).matches();
            if (pendiente) {
                // Evitar datos de identificación y detalles del consumidor en el aviso.
                jdbc.update("""
                    INSERT INTO public.saas_reclamaciones_notificaciones
                      (tienda_id,reclamacion_id,evento_id,clase,destinatario,asunto,cuerpo)
                    VALUES (?,?,?,'RESPUESTA_CONSUMIDOR',?,?,?)
                    """, tenant, encontrados.get(0).reclamacionId(), eventoId, correo,
                    "Respuesta a su reclamación N.º " + numeroHoja,
                    "Se registró una respuesta formal a su reclamación N.º " + numeroHoja
                       + ". Contacte al proveedor por un canal verificado para obtenerla de forma segura.");
            }
            return new Resultado(numeroHoja, pendiente);
        });
    }

    private record Destinatario(long reclamacionId, String correo) {}
}
