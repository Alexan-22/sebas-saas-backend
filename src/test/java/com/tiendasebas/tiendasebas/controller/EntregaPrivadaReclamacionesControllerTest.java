package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.security.AuthRateLimit;
import com.tiendasebas.tiendasebas.service.EntregaPrivadaReclamacionesService;
import com.tiendasebas.tiendasebas.service.LibroReclamacionesService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Contrato unitario del controlador. No sustituye pruebas HTTP de Spring Security y RLS. */
class EntregaPrivadaReclamacionesControllerTest {
    private final EntregaPrivadaReclamacionesService servicio = mock(EntregaPrivadaReclamacionesService.class);
    private final AuthRateLimit limites = mock(AuthRateLimit.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final LibroReclamacionesService.Solicitud solicitud = mock(LibroReclamacionesService.Solicitud.class);

    @AfterEach void limpiar() { TenantContext.clear(); }

    @Test void deshabilitadoNoAccedeAServicios() {
        var controller = new EntregaPrivadaReclamacionesController(servicio, limites, false);
        var err = assertThrows(ResponseStatusException.class, () -> controller.registrar(solicitud, request));
        assertEquals(HttpStatus.NOT_FOUND, err.getStatusCode());
        verifyNoInteractions(servicio, limites, request);
    }

    @Test void tenantAusenteRechazaRegistro() {
        var controller = new EntregaPrivadaReclamacionesController(servicio, limites, true);
        var err = assertThrows(ResponseStatusException.class, () -> controller.registrar(solicitud, request));
        assertEquals(HttpStatus.UNAUTHORIZED, err.getStatusCode());
        verifyNoInteractions(servicio, limites);
    }

    @Test void registroResponde201ConTokenYNoStore() {
        TenantContext.setCurrentTenant(7L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servicio.registrar(solicitud)).thenReturn(new EntregaPrivadaReclamacionesService.Confirmacion(12L, "token-prueba"));
        var controller = new EntregaPrivadaReclamacionesController(servicio, limites, true);
        var response = controller.registrar(solicitud, request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(12L, response.getBody().numeroHoja());
        assertEquals("token-prueba", response.getBody().tokenConstancia());
        verify(limites).comprobar("reclamaciones-entrega:7", "127.0.0.1", 5);
        verify(servicio).registrar(solicitud);
    }

    @Test void constanciaRechazaOrigenVacio() {
        TenantContext.setCurrentTenant(7L);
        when(request.getRemoteAddr()).thenReturn(" ");
        var controller = new EntregaPrivadaReclamacionesController(servicio, limites, true);
        var err = assertThrows(ResponseStatusException.class,
                () -> controller.constancia(new EntregaPrivadaReclamacionesController.ConsultaToken("abc"), request));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, err.getStatusCode());
        verifyNoInteractions(servicio);
    }

    @Test void constanciaDevuelveHtmlConCabecerasSeguras() {
        TenantContext.setCurrentTenant(7L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servicio.html("token-prueba")).thenReturn("<html><body>Hoja</body></html>");
        var controller = new EntregaPrivadaReclamacionesController(servicio, limites, true);
        var response = controller.constancia(new EntregaPrivadaReclamacionesController.ConsultaToken("token-prueba"), request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("no-referrer", response.getHeaders().getFirst("Referrer-Policy"));
        assertTrue(response.getHeaders().getFirst("Content-Security-Policy").contains("sandbox"));
        assertTrue(response.getBody().contains("Hoja"));
        verify(servicio).html("token-prueba");
    }
}
