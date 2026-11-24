package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.security.AuthRateLimit;
import com.tiendasebas.tiendasebas.service.LibroReclamacionesService;
import com.tiendasebas.tiendasebas.service.LibroReclamacionesService.Solicitud;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pruebas unitarias del contrato del controlador; NO habilitan la ruta en Spring Security. */
class LibroReclamacionesControllerTest {
    private final LibroReclamacionesService servicio = mock(LibroReclamacionesService.class);
    private final AuthRateLimit limites = mock(AuthRateLimit.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final Solicitud solicitud = mock(Solicitud.class);

    @AfterEach void limpiarTenant() {
        TenantContext.clear();
    }

    @Test void registroDeshabilitadoPorDefectoNoInvocaDependencias() {
        var controlador = new LibroReclamacionesController(servicio, limites, false);
        var error = assertThrows(ResponseStatusException.class,
                () -> controlador.registrar(solicitud, request));
        assertEquals(HttpStatus.NOT_FOUND.value(), error.getStatusCode().value());
        verifyNoInteractions(servicio, limites, request);
    }

    @Test void faltaTenantNoRegistra() {
        var controlador = new LibroReclamacionesController(servicio, limites, true);
        var error = assertThrows(ResponseStatusException.class,
                () -> controlador.registrar(solicitud, request));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), error.getStatusCode().value());
        verifyNoInteractions(servicio, limites, request);
    }

    @Test void origenInvalidoNoRegistra() {
        TenantContext.setCurrentTenant(12L);
        when(request.getRemoteAddr()).thenReturn(" ");
        var controlador = new LibroReclamacionesController(servicio, limites, true);
        var error = assertThrows(ResponseStatusException.class,
                () -> controlador.registrar(solicitud, request));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), error.getStatusCode().value());
        verifyNoInteractions(servicio, limites);
    }

    @Test void registradoDevuelve201NumeroYCacheNoStoreSinIdInterno() {
        TenantContext.setCurrentTenant(12L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servicio.registrar(solicitud)).thenReturn(new LibroReclamacionesService.Registro(7L, 999L));
        var controlador = new LibroReclamacionesController(servicio, limites, true);
        var respuesta = controlador.registrar(solicitud, request);
        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        assertEquals(7L, respuesta.getBody().numeroHoja());
        assertEquals("no-store", respuesta.getHeaders().getCacheControl());
        assertEquals(1, respuesta.getBody().getClass().getRecordComponents().length);
        verify(limites).comprobar("reclamaciones:12", "127.0.0.1", 5);
        verify(servicio).registrar(solicitud);
    }

    @Test void limiteExcedidoNoRegistra() {
        TenantContext.setCurrentTenant(12L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .when(limites).comprobar("reclamaciones:12", "127.0.0.1", 5);
        var controlador = new LibroReclamacionesController(servicio, limites, true);
        var error = assertThrows(ResponseStatusException.class,
                () -> controlador.registrar(solicitud, request));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), error.getStatusCode().value());
        verifyNoInteractions(servicio);
    }
}
