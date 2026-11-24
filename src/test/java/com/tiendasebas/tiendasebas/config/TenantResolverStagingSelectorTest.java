package com.tiendasebas.tiendasebas.config;

import com.tiendasebas.tiendasebas.entity.Tienda;
import com.tiendasebas.tiendasebas.repository.TiendaRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantResolverStagingSelectorTest {
    private TiendaRepository tiendas;
    private TenantResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void configurar() {
        tiendas = mock(TiendaRepository.class);
        resolver = new TenantResolver(tiendas, mock(JdbcTemplate.class));
        request = mock(HttpServletRequest.class);
        ReflectionTestUtils.setField(resolver, "stagingEnabled", true);
        ReflectionTestUtils.setField(resolver, "rlsAuthEnabled", false);
        ReflectionTestUtils.setField(resolver, "activeProfile", "dev");
        ReflectionTestUtils.setField(resolver, "stagingHost", "sebas-saas-api-dev.azurewebsites.net");
        ReflectionTestUtils.setField(resolver, "stagingSubdomain", "sebascollection");
        ReflectionTestUtils.setField(resolver, "stagingSelectorEnabled", true);
        ReflectionTestUtils.setField(resolver, "stagingAllowedSubdomains", "amyfashion");
        ReflectionTestUtils.setField(resolver, "baseDomain", "example.test");
        when(request.getServerName()).thenReturn("sebas-saas-api-dev.azurewebsites.net");
        var sebas = mock(Tienda.class);
        when(sebas.getId()).thenReturn(1L);
        when(sebas.getEstado()).thenReturn("ACTIVA");
        var amy = mock(Tienda.class);
        when(amy.getId()).thenReturn(10L);
        when(amy.getEstado()).thenReturn("ACTIVA");
        when(tiendas.findBySubdominio("sebascollection")).thenReturn(Optional.of(sebas));
        when(tiendas.findBySubdominio("amyfashion")).thenReturn(Optional.of(amy));
    }

    @Test void sinSelectorConservaSebasCollection() {
        when(request.getHeaders("X-Storefront-Slug")).thenReturn(Collections.emptyEnumeration());
        assertEquals(Optional.of(1L), resolver.resolver(request));
    }

    @Test void selectorPermitidoResuelveAmyFashion() {
        when(request.getHeaders("X-Storefront-Slug")).thenReturn(Collections.enumeration(java.util.List.of("amyfashion")));
        assertEquals(Optional.of(10L), resolver.resolver(request));
    }

    @Test void selectorNoPermitidoFallaCerrado() {
        when(request.getHeaders("X-Storefront-Slug")).thenReturn(Collections.enumeration(java.util.List.of("sebascollection")));
        assertTrue(resolver.resolver(request).isEmpty());
    }

    @Test void encabezadosDuplicadosSonRechazados() {
        when(request.getHeaders("X-Storefront-Slug")).thenReturn(Collections.enumeration(java.util.List.of("amyfashion", "amyfashion")));
        assertTrue(resolver.resolver(request).isEmpty());
    }

    @Test void featureDeshabilitadaFallaCerrado() {
        ReflectionTestUtils.setField(resolver, "stagingSelectorEnabled", false);
        when(request.getHeaders("X-Storefront-Slug")).thenReturn(Collections.enumeration(java.util.List.of("amyfashion")));
        assertTrue(resolver.resolver(request).isEmpty());
    }

    @Test void otroHostNoPuedeUsarElAlias() {
        when(request.getServerName()).thenReturn("otro.azurewebsites.net");
        when(request.getHeaders("X-Storefront-Slug")).thenReturn(Collections.enumeration(java.util.List.of("amyfashion")));
        assertTrue(resolver.resolver(request).isEmpty());
    }

    @Test void tiendaSuspendidaNoResuelve() {
        var amy = mock(Tienda.class);
        when(amy.getEstado()).thenReturn("SUSPENDIDA");
        when(tiendas.findBySubdominio("amyfashion")).thenReturn(Optional.of(amy));
        when(request.getHeaders("X-Storefront-Slug")).thenReturn(Collections.enumeration(java.util.List.of("amyfashion")));
        assertTrue(resolver.resolver(request).isEmpty());
    }
}
