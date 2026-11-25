package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DespachoCorreoReclamacionesServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> senders = mock(ObjectProvider.class);

    @AfterEach void limpiar() { TenantContext.clear(); }

    @Test void correoDeshabilitadoNoConsultaNiEnvia() {
        var servicio = new DespachoCorreoReclamacionesService(jdbc, tm, senders, false, "avisos@example.org");
        var error = assertThrows(ResponseStatusException.class, () -> servicio.procesar(1));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(jdbc, tm, senders);
    }

    @Test void limiteCeroRechazadoAntesDeAccederADatos() {
        var servicio = new DespachoCorreoReclamacionesService(jdbc, tm, senders, true, "avisos@example.org");
        var error = assertThrows(ResponseStatusException.class, () -> servicio.procesar(0));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc, tm, senders);
    }

    @Test void limiteExcesivoRechazado() {
        var servicio = new DespachoCorreoReclamacionesService(jdbc, tm, senders, true, "avisos@example.org");
        var error = assertThrows(ResponseStatusException.class, () -> servicio.procesar(21));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc, tm, senders);
    }

    @Test void sinRemitenteNoProcesa() {
        var servicio = new DespachoCorreoReclamacionesService(jdbc, tm, senders, true, " ");
        var error = assertThrows(ResponseStatusException.class, () -> servicio.procesar(1));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verifyNoInteractions(jdbc, tm, senders);
    }

    @Test void sinTransporteSmtpNoProcesa() {
        when(senders.getIfAvailable()).thenReturn(null);
        var servicio = new DespachoCorreoReclamacionesService(jdbc, tm, senders, true, "avisos@example.org");
        var error = assertThrows(ResponseStatusException.class, () -> servicio.procesar(1));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verifyNoInteractions(jdbc, tm);
    }
}
