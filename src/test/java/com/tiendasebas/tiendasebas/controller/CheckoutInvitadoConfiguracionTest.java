package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.CompraInvitadoRequest;
import com.tiendasebas.tiendasebas.service.CheckoutInvitadoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CheckoutInvitadoConfiguracionTest {
    private static final String FLAG = "sebas.checkout.invitado.enabled";
    private final CheckoutInvitadoService checkout = mock(CheckoutInvitadoService.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final CheckoutInvitadoController controller = new CheckoutInvitadoController(checkout, environment);
    private final CompraInvitadoRequest request = new CompraInvitadoRequest();

    @Test
    void deniegaSinConfiguracion() {
        assertDenegado();
    }

    @Test
    void deniegaUnaConfiguracionInvalidaEnPruebas() {
        environment.setActiveProfiles("checkout-invitado-test");
        environment.setProperty(FLAG, "si");
        assertDenegado();
    }

    @Test
    void deniegaUnPerfilOrdinarioAunqueLaPropiedadEsteActiva() {
        environment.setActiveProfiles("dev");
        environment.setProperty(FLAG, "true");
        assertDenegado();
    }

    @Test
    void deniegaProduccionAunqueLaConfiguracionDePruebasEsteActiva() {
        environment.setActiveProfiles("prod", "checkout-invitado-test");
        environment.setProperty(FLAG, "true");
        assertDenegado();
    }

    @Test
    void delegaLaCompraSoloConElPerfilYPropiedadExplicitamenteHabilitados() {
        environment.setActiveProfiles("checkout-invitado-test");
        environment.setProperty(FLAG, "true");
        assertEquals(HttpStatus.OK, controller.comprar(request).getStatusCode());
        verify(checkout).comprar(request);
    }

    private void assertDenegado() {
        var response = controller.comprar(request);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        verifyNoInteractions(checkout);
    }
}
