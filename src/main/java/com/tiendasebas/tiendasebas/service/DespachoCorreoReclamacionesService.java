package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Despacho explicito por tienda de un outbox duradero.
 * No ejecuta jobs globales que atraviesen el aislamiento RLS.
 * Requiere SMTP real configurado y habilitacion explicita.
 */
@Service
public class DespachoCorreoReclamacionesService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectProvider<JavaMailSender> transportes;
    private final boolean habilitado;
    private final String remitente;

    public DespachoCorreoReclamacionesService(JdbcTemplate jdbc, PlatformTransactionManager manager,
            ObjectProvider<JavaMailSender> transportes,
            @Value("${app.reclamaciones.correos-habilitados:false}") boolean habilitado,
            @Value("${app.reclamaciones.correo-remitente:}") String remitente) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.transportes = transportes;
        this.habilitado = habilitado;
        this.remitente = remitente;
    }

    public record Resultado(int enviados, int fallidos) {}
    private record Correo(long id, String destinatario, String asunto, String cuerpo) {}

    public Resultado procesar(int limite) {
        if (!habilitado) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Correos deshabilitados");
        if (limite < 1 || limite > 20) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limite invalido");
        if (remitente == null || remitente.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Remitente sin configurar");
        var sender = transportes.getIfAvailable();
        if (sender == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMTP sin configurar");
        long tenant = TenantContext.getCurrentTenant();
        int enviados = 0, fallidos = 0;
        for (int i = 0; i < limite; i++) {
            Correo correo = tx.execute(status -> {
                contexto(tenant);
                List<Correo> pendientes = jdbc.query("""
                        SELECT id, destinatario, asunto, cuerpo
                          FROM public.saas_reclamaciones_notificaciones
                         WHERE tienda_id = ? AND estado = 'PENDIENTE'
                         ORDER BY creado_en, id LIMIT 1 FOR UPDATE SKIP LOCKED
                        """, (rs, n) -> new Correo(rs.getLong(1), rs.getString(2),
                                rs.getString(3), rs.getString(4)), tenant);
                if (pendientes.isEmpty()) return null;
                Correo primero = pendientes.get(0);
                int cambios = jdbc.update("""
                        UPDATE public.saas_reclamaciones_notificaciones
                           SET estado = 'ENVIANDO', intentos = intentos + 1, error_ultimo = NULL
                         WHERE tienda_id = ? AND id = ? AND estado = 'PENDIENTE'
                        """, tenant, primero.id());
                return cambios == 1 ? primero : null;
            });
            if (correo == null) break;
            boolean exito = false;
            String error = null;
            try {
                SimpleMailMessage mensaje = new SimpleMailMessage();
                mensaje.setFrom(remitente);
                mensaje.setTo(correo.destinatario());
                mensaje.setSubject(correo.asunto());
                mensaje.setText(correo.cuerpo());
                sender.send(mensaje);
                exito = true;
            } catch (Exception e) {
                // Nunca persistir excepciones que pueden incluir credenciales SMTP.
                error = "Error de transporte SMTP; revisar registros seguros del servicio";
            }
            final boolean enviado = exito;
            final String causa = error;
            tx.executeWithoutResult(status -> {
                contexto(tenant);
                jdbc.update("""
                        UPDATE public.saas_reclamaciones_notificaciones
                           SET estado = ?, enviado_en = CASE WHEN ? THEN clock_timestamp() ELSE NULL END,
                               error_ultimo = ?
                         WHERE tienda_id = ? AND id = ? AND estado = 'ENVIANDO'
                        """, enviado ? "ENVIADO" : "ERROR", enviado, causa, tenant, correo.id());
            });
            if (exito) enviados++; else fallidos++;
        }
        return new Resultado(enviados, fallidos);
    }

    private void contexto(long tenant) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenant));
    }
}
