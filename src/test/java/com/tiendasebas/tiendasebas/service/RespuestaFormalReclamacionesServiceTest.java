package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RespuestaFormalReclamacionesServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    private final RespuestaFormalReclamacionesService servicio = new RespuestaFormalReclamacionesService(jdbc, tm);

    @AfterEach void limpiar() { TenantContext.clear(); }

    @Test void hojaInvalidaSeRechaza() {
        var error = assertThrows(ResponseStatusException.class, () -> servicio.responder(0, "Respuesta", "admin"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc, tm);
    }

    @Test void respuestaVaciaSeRechaza() {
        var error = assertThrows(ResponseStatusException.class, () -> servicio.responder(1, "  ", "admin"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc, tm);
    }

    @Test void respuestaExtensaSeRechaza() {
        var error = assertThrows(ResponseStatusException.class, () -> servicio.responder(1, "x".repeat(12001), "admin"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc, tm);
    }

    @Test void actorAusenteSeRechaza() {
        var error = assertThrows(ResponseStatusException.class, () -> servicio.responder(1, "Respuesta", null));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(jdbc, tm);
    }
}
