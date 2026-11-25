package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.CompraInvitadoRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class CheckoutInvitadoIdempotenciaService {

    private final JdbcTemplate jdbc;

    public CheckoutInvitadoIdempotenciaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private long tiendaActual() {
        Long tiendaId = TenantContext.getCurrentTenant();

        if (tiendaId == null || tiendaId <= 0) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Tienda no identificada"
            );
        }

        return tiendaId;
    }

    private UUID clave(CompraInvitadoRequest request) {
        try {
            String valor = request.getClaveIdempotencia();
            UUID uuid = UUID.fromString(valor);

            if (!uuid.toString().equalsIgnoreCase(valor)) {
                throw new IllegalArgumentException();
            }

            return uuid;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Se requiere claveIdempotencia UUID"
            );
        }
    }

    private static String hash(String valor) {
        try {
            byte[] bytes = MessageDigest
                .getInstance("SHA-256")
                .digest(valor.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(bytes);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String huella(CompraInvitadoRequest request) {

        if (request == null || request.getCliente() == null
                || request.getDireccionEnvio() == null
                || request.getItems() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Solicitud de compra invalida"
            );
        }

        StringBuilder datos = new StringBuilder();

        var cliente = request.getCliente();
        var direccion = request.getDireccionEnvio();

        datos.append(cliente.getNombres()).append('|')
             .append(cliente.getApellidos()).append('|')
             .append(cliente.getCorreo()).append('|')
             .append(cliente.getTelefono()).append('|')
             .append(request.getMedioPago()).append('|')
             .append(request.getCuentaBancariaId()).append('|')
             .append(direccion.getDepartamento()).append('|')
             .append(direccion.getProvincia()).append('|')
             .append(direccion.getDistrito()).append('|')
             .append(direccion.getDireccionExacta()).append('|')
             .append(direccion.getReferencia());

        request.getItems().stream()
            .sorted(java.util.Comparator.comparing(
                item -> item.getIdProductoTalla(),
                java.util.Comparator.nullsFirst(
                    java.util.Comparator.naturalOrder()
                )
            ))
            .forEach(item -> datos.append('|')
                .append(item.getIdProductoTalla())
                .append(':')
                .append(item.getCantidad())
            );

        return hash(datos.toString());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Integer recuperar(
            CompraInvitadoRequest request
    ) {
        long tienda = tiendaActual();
        UUID uuid = clave(request);

        String bloqueo = "checkout-invitado:" + tienda + ":" + uuid;

        jdbc.query(
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
            rs -> {},
            bloqueo
        );

        var anteriores = jdbc.query("""
            SELECT huella, id_venta
            FROM public.checkout_invitado_reintentos
            WHERE tienda_id = ? AND clave = ?
            """,
            (rs, fila) -> new Object[]{
                rs.getString("huella"),
                rs.getInt("id_venta")
            },
            tienda,
            uuid
        );

        if (anteriores.isEmpty()) {
            return null;
        }

        Object[] anterior = anteriores.getFirst();

        if (!huella(request).equals(anterior[0])) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "La clave ya pertenece a otra compra"
            );
        }

        return (Integer) anterior[1];
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordar(
            CompraInvitadoRequest request,
            Integer idVenta
    ) {
        long tienda = tiendaActual();

        jdbc.update("""
            INSERT INTO public.checkout_invitado_reintentos
                (tienda_id, clave, huella, id_venta)
            VALUES (?, ?, ?, ?)
            """,
            tienda,
            clave(request),
            huella(request),
            idVenta
        );
    }
}