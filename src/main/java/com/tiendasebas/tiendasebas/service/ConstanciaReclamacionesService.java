package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lectura interna de la hoja persistida. NO expone endpoints HTTP ni genera
 * un PDF legal. Acceso exclusivo desde flujos autenticados/autorizados futuros.
 *
 * Requiere que el transaction manager y JdbcTemplate compartan la misma
 * conexion para que set_config(..., true) aplique a la consulta bajo RLS.
 */
@Service
public class ConstanciaReclamacionesService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public ConstanciaReclamacionesService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.tx.setReadOnly(true);
    }

    public record Hoja(
            long numeroHoja,
            OffsetDateTime recibidoEn,
            String tipo,
            String consumidorNombre,
            String documentoTipo,
            String documentoNumero,
            String domicilio,
            String correo,
            String telefono,
            boolean menorEdad,
            String representanteNombre,
            String bienTipo,
            String bienDescripcion,
            BigDecimal montoReclamado,
            String detalle,
            String pedido
    ) {}

    /**
     * Recupera SOLO la hoja de la tienda establecida por el filtro de seguridad.
     * Los datos devueltos son privados; no exponer esta clase directamente por HTTP.
     */
    public Hoja obtener(long numeroHoja) {
        if (numeroHoja <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Numero de hoja invalido");
        }
        long tiendaId = TenantContext.getCurrentTenant();
        Hoja hoja = tx.execute(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, Long.toString(tiendaId));
            return jdbc.query("""
                    SELECT numero_hoja, recibido_en, tipo, consumidor_nombre,
                           documento_tipo, documento_numero, domicilio, correo,
                           telefono, menor_edad, representante_nombre,
                           bien_tipo, bien_descripcion, monto_reclamado, detalle, pedido
                    FROM public.saas_reclamaciones
                    WHERE tienda_id = ? AND numero_hoja = ?
                    """, rs -> {
                if (!rs.next()) return null;
                return new Hoja(
                        rs.getLong("numero_hoja"),
                        rs.getObject("recibido_en", OffsetDateTime.class),
                        rs.getString("tipo"),
                        rs.getString("consumidor_nombre"),
                        rs.getString("documento_tipo"),
                        rs.getString("documento_numero"),
                        rs.getString("domicilio"),
                        rs.getString("correo"),
                        rs.getString("telefono"),
                        rs.getBoolean("menor_edad"),
                        rs.getString("representante_nombre"),
                        rs.getString("bien_tipo"),
                        rs.getString("bien_descripcion"),
                        rs.getBigDecimal("monto_reclamado"),
                        rs.getString("detalle"),
                        rs.getString("pedido")
                );
            }, tiendaId, numeroHoja);
        });
        if (hoja == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hoja no encontrada");
        }
        return hoja;
    }
}
