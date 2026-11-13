package com.tiendasebas.tiendasebas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Vincula un JWT al hash actual de credenciales sin publicar el hash.
     * Tras cambiar la contrasena, todos los JWT anteriores dejan de ser validos.
     */
    public String versionCredenciales(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Hash de credenciales obligatorio");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] firma = mac.doFinal(("sebas-credenciales-v1:" + passwordHash)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo validar el estado de credenciales", e);
        }
    }

    public boolean credencialesVigentes(String claim, String passwordHash) {
        if (claim == null || claim.isBlank() || passwordHash == null) {
            return false;
        }
        try {
            byte[] presentado = Base64.getUrlDecoder().decode(claim);
            byte[] actual = Base64.getUrlDecoder().decode(versionCredenciales(passwordHash));
            return MessageDigest.isEqual(presentado, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Emision de nuevos tokens con estado de credenciales. */
    public String generateToken(
            Integer usuarioId, String correo, String rol, Long tiendaId,
            String passwordHash
    ) {
        JwtBuilder builder = Jwts.builder()
                .subject(correo)
                .claim("uid", usuarioId)
                .claim("rol", rol)
                .claim("cv", versionCredenciales(passwordHash));

        if (tiendaId != null) {
            builder.claim("tid", tiendaId);
        }
        return builder
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key())
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
