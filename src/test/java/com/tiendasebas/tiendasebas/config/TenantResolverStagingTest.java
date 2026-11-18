
package com.tiendasebas.tiendasebas.config;

import com.tiendasebas.tiendasebas.entity.Tienda;
import com.tiendasebas.tiendasebas.repository.TiendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantResolverStagingTest {

    private TiendaRepository tiendas;
    private TenantResolver resolver;

    @BeforeEach
    void preparar() {
        tiendas = mock(TiendaRepository.class);

        resolver = new TenantResolver(
                tiendas,
                mock(JdbcTemplate.class)
        );

        ReflectionTestUtils.setField(
                resolver, "stagingEnabled", true
        );
        ReflectionTestUtils.setField(
                resolver, "stagingHost",
                "sebas-saas-api-dev.azurewebsites.net"
        );
        ReflectionTestUtils.setField(
                resolver, "stagingSubdomain",
                "sebascollection"
        );
        ReflectionTestUtils.setField(
                resolver, "activeProfile", "dev"
        );
        ReflectionTestUtils.setField(
                resolver, "rlsAuthEnabled", false
        );
        ReflectionTestUtils.setField(
                resolver, "baseDomain", "azurewebsites.net"
        );
        ReflectionTestUtils.setField(
                resolver, "customDomainsEnabled", false
        );
    }

    private MockHttpServletRequest solicitud(String host) {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setServerName(host);
        return request;
    }

    private void tienda(String estado) {
        Tienda tienda = new Tienda();
        tienda.setId(1L);
        tienda.setSubdominio("sebascollection");
        tienda.setEstado(estado);

        when(tiendas.findBySubdominio("sebascollection"))
                .thenReturn(Optional.of(tienda));
    }

    @Test
    void permiteTiendaActivaEnHostExacto() {
        tienda("ACTIVA");

        var resultado = resolver.resolver(
                solicitud("sebas-saas-api-dev.azurewebsites.net")
        );

        assertEquals(Optional.of(1L), resultado);
    }

    @Test
    void noAceptaHostDiferente() {
        var resultado = resolver.resolver(
                solicitud("otro-api.azurewebsites.net")
        );

        assertTrue(resultado.isEmpty());
        verify(tiendas, never())
                .findBySubdominio("sebascollection");
    }

    @Test
    void bloqueaTiendaSuspendida() {
        tienda("SUSPENDIDA");

        var resultado = resolver.resolver(
                solicitud("sebas-saas-api-dev.azurewebsites.net")
        );

        assertTrue(resultado.isEmpty());
    }

    @Test
    void bloqueaAliasDeshabilitado() {
        ReflectionTestUtils.setField(
                resolver, "stagingEnabled", false
        );

        var resultado = resolver.resolver(
                solicitud("sebas-saas-api-dev.azurewebsites.net")
        );

        assertTrue(resultado.isEmpty());
    }

    @Test
    void bloqueaAliasEnProduccion() {
        ReflectionTestUtils.setField(
                resolver, "activeProfile", "prod"
        );

        var resultado = resolver.resolver(
                solicitud("sebas-saas-api-dev.azurewebsites.net")
        );

        assertTrue(resultado.isEmpty());
    }
}
