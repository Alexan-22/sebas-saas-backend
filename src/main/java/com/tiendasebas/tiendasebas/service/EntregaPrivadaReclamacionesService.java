package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Primer paso funcional: token secreto de 256 bits, hash SHA-256 persistido, alcance tenant.
 * NOTA: exige V24, V23 y pruebas de permisos en staging; no habilitar en produccion sin ellas.
 */
@Service
public class EntregaPrivadaReclamacionesService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final LibroReclamacionesService registro;
    private final ConstanciaReclamacionesService constancias;
    private final ConstanciaReclamacionesDocumentoService documentos;

    public EntregaPrivadaReclamacionesService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                             LibroReclamacionesService registro,
                                             ConstanciaReclamacionesService constancias,
                                             ConstanciaReclamacionesDocumentoService documentos) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.registro = registro;
        this.constancias = constancias;
        this.documentos = documentos;
    }

    public record Confirmacion(long numeroHoja, String tokenConstancia) {}
    private record AvisoAdmin(String correo, long eventoId) {}

    public Confirmacion registrar(LibroReclamacionesService.Solicitud solicitud) {
        long tenant = TenantContext.getCurrentTenant();
        // Primero se comprueba la identidad legal: nunca aceptar una hoja si no se puede emitir constancia.
        return tx.execute(s -> {
            contexto(tenant);
            proveedor(tenant);
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            String hash = sha256(token);
            // TransactionTemplate interno con PROPAGATION_REQUIRED se une a esta transaccion.
            var hoja = registro.registrar(solicitud);
            jdbc.update("""
                    INSERT INTO public.saas_reclamaciones_accesos
                    (tienda_id, reclamacion_id, token_sha256)
                    VALUES (?, ?, ?)
                    """, tenant, hoja.id(), hash);
            // Notificacion administrativa en outbox, atomica con la recepcion.
            // Solo se genera si el correo del administrador esta verificado.
            var correos = jdbc.query("""
                    SELECT c.correo_admin, e.id
                      FROM public.saas_reclamaciones_correos_config c
                      JOIN public.saas_reclamaciones_eventos e
                        ON e.tienda_id = c.tienda_id AND e.reclamacion_id = ?
                     WHERE c.tienda_id = ? AND c.correo_admin_verificado = true
                       AND e.tipo = 'RECEPCION'
                     ORDER BY e.id LIMIT 1
                    """, (rs, i) -> new AvisoAdmin(rs.getString(1), rs.getLong(2)), hoja.id(), tenant);
            if (correos.size() == 1 && correos.get(0).correo() != null
                    && correos.get(0).correo().length() <= 254
                    && correos.get(0).correo().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                jdbc.update("""
                        INSERT INTO public.saas_reclamaciones_notificaciones
                          (tienda_id,reclamacion_id,evento_id,clase,destinatario,asunto,cuerpo)
                        VALUES (?,?,?,'RECLAMACION_NUEVA',?,?,?)
                        ON CONFLICT (tienda_id,evento_id,clase) DO NOTHING
                        """, tenant, hoja.id(), correos.get(0).eventoId(),
                        correos.get(0).correo(), "Nueva reclamacion N.º " + hoja.numeroHoja(),
                        "Se registro una nueva reclamacion N.º " + hoja.numeroHoja()
                            + ". Ingrese al panel de administracion para revisarla.");
            }
            return new Confirmacion(hoja.numeroHoja(), token);
        });
    }

    /** Token se recibe por POST body, nunca por parametro URL. */
    public String html(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Constancia no disponible");
        }
        long tenant = TenantContext.getCurrentTenant();
        return tx.execute(s -> {
            contexto(tenant);
            List<Long> numeros = jdbc.query("""
                    SELECT r.numero_hoja
                    FROM public.saas_reclamaciones_accesos a
                    JOIN public.saas_reclamaciones r
                      ON r.tienda_id = a.tienda_id AND r.id = a.reclamacion_id
                    WHERE a.tienda_id = ? AND a.token_sha256 = ? AND a.revocado_en IS NULL
                    """, (rs, i) -> rs.getLong(1), tenant, sha256(token));
            if (numeros.size() != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Constancia no disponible");
            }
            var hoja = constancias.obtener(numeros.get(0));
            return documentos.generarHtml(hoja, proveedor(tenant));
        });
    }

    private ConstanciaReclamacionesDocumentoService.Proveedor proveedor(long tenant) {
        var proveedores = jdbc.query("""
                SELECT razon_social, ruc, domicilio_legal
                FROM public.saas_reclamaciones_proveedores
                WHERE tienda_id = ? AND verificado = true
                """, (rs, i) -> new ConstanciaReclamacionesDocumentoService.Proveedor(
                rs.getString(1), rs.getString(2), rs.getString(3)), tenant);
        if (proveedores.size() != 1) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Libro no configurado");
        }
        return proveedores.get(0);
    }

    private void contexto(long tenant) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, Long.toString(tenant));
    }

    private static String sha256(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }
}
