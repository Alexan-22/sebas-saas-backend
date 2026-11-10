package com.tiendasebas.tiendasebas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
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

    public String generateToken(
            Integer usuarioId,
            String correo,
            String rol,
            Long tiendaId
    ) {
        JwtBuilder builder = Jwts.builder()
                .subject(correo)
                .claim("uid", usuarioId)
                .claim("rol", rol);

        // Los usuarios normales pertenecen a una tienda.
        // El SUPER_ADMIN global no tiene tienda_id.
        if (tiendaId != null) {
            builder.claim("tid", tiendaId);
        }

        return builder
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis() + expiration
                        )
                )
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
