package com.tiendasebas.tiendasebas.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtCredencialesRevocacionTest {

    private JwtService jwt;

    @BeforeEach
    void configurar() {
        jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "secret",
                "clave-segura-de-pruebas-de-64-caracteres-para-firma-jwt-sebas-2026");
        ReflectionTestUtils.setField(jwt, "expiration", 3600000L);
    }

    @Test
    void tokenValidoConHashActual() {
        String hash = "$2a$10$hash-actual-de-prueba";
        String token = jwt.generateToken(7, "admin@ejemplo.test", "EMPLEADO", 1L, hash);
        Claims claims = jwt.getClaims(token);
        assertEquals("admin@ejemplo.test", claims.getSubject());
        assertTrue(jwt.credencialesVigentes(claims.get("cv", String.class), hash));
    }

    @Test
    void cambiarHashRevocaTokenAnterior() {
        String anterior = "$2a$10$hash-anterior";
        String nuevo = "$2a$10$hash-nuevo";
        String tokenAnterior = jwt.generateToken(7, "admin@ejemplo.test", "EMPLEADO", 1L, anterior);
        Claims claims = jwt.getClaims(tokenAnterior);
        assertFalse(jwt.credencialesVigentes(claims.get("cv", String.class), nuevo));
    }

    @Test
    void tokenSinVersionNoEsAceptado() {
        assertFalse(jwt.credencialesVigentes(null, "$2a$10$hash-actual"));
    }

    @Test
    void noAceptaVersionManipulada() {
        assertFalse(jwt.credencialesVigentes("valor-invalido", "$2a$10$hash-actual"));
    }

    @Test
    void superAdminSinTiendaTambienQuedaProtegido() {
        String token = jwt.generateToken(1, "super@ejemplo.test", "SUPER_ADMIN", null,
                "$2a$10$hash-superadmin");
        Claims claims = jwt.getClaims(token);
        assertNull(claims.get("tid"));
        assertTrue(jwt.credencialesVigentes(claims.get("cv", String.class),
                "$2a$10$hash-superadmin"));
    }
}
