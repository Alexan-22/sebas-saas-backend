package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class CheckoutInvitadoAccesoService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration VIGENCIA = Duration.ofDays(30);

    private final JdbcTemplate jdbc;

    public CheckoutInvitadoAccesoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private long tiendaActual() {
        Long tienda = TenantContext.getCurrentTenant();

        if (tienda == null || tienda <= 0) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Tienda no identificada"
            );
        }

        return tienda;
    }

    private static String hash(String token) {
        try {
            byte[] bytes = MessageDigest
                .getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(bytes);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String generarAcceso(Integer idVenta) {

        long tienda = tiendaActual();

        if (idVenta == null || idVenta <= 0) {
            throw new IllegalArgumentException("Venta invalida");
        }

        byte[] aleatorios = new byte[32];
        RANDOM.nextBytes(aleatorios);

        String token = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(aleatorios);

        jdbc.update("""
            INSERT INTO public.checkout_invitado_accesos
                (tienda_id, id_venta, token_hash, vence_en)
            VALUES (?, ?, ?, now() + (? * interval '1 second'))
            """,
            tienda,
            idVenta,
            hash(token),
            VIGENCIA.toSeconds()
        );

        return token;
    }

    @Transactional(readOnly = true)
    public boolean accesoValido(Integer idVenta, String token) {

        long tienda = tiendaActual();

        if (idVenta == null || idVenta <= 0 ||
            token == null ||
            !token.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }

        Integer cantidad = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM public.checkout_invitado_accesos
            WHERE tienda_id = ?
              AND id_venta = ?
              AND token_hash = ?
              AND vence_en > now()
            """,
            Integer.class,
            tienda,
            idVenta,
            hash(token)
        );

        return cantidad != null && cantidad == 1;
    }
}