package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RbacRevocacionTenantTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private final RbacAutorizacionService rbac =
            new RbacAutorizacionService(jdbc);

    private Authentication empleado() {
        return new UsernamePasswordAuthenticationToken(
                "empleado@example.test",
                null,
                List.of(
                    new SimpleGrantedAuthority("ROLE_EMPLEADO")
                )
        );
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void revocacionDebeAplicarseConMismaAutenticacion() {

        TenantContext.setCurrentTenant(1L);

        Authentication autenticacion = empleado();

        when(jdbc.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("empleado@example.test"),
                eq(1L),
                eq("ventas.ver")
        )).thenReturn(true, false);

        assertTrue(rbac.tienePermiso(
                autenticacion, "ventas.ver"
        ));

        assertFalse(rbac.tienePermiso(
                autenticacion, "ventas.ver"
        ));

        verify(jdbc, times(2)).queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("empleado@example.test"),
                eq(1L),
                eq("ventas.ver")
        );
    }

    @Test
    void permisosSeConsultanPorTiendaActual() {

        Authentication autenticacion = empleado();

        when(jdbc.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("empleado@example.test"),
                eq(1L),
                eq("ventas.ver")
        )).thenReturn(true);

        when(jdbc.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("empleado@example.test"),
                eq(2L),
                eq("ventas.ver")
        )).thenReturn(false);

        TenantContext.setCurrentTenant(1L);

        assertTrue(rbac.tienePermiso(
                autenticacion, "ventas.ver"
        ));

        TenantContext.clear();
        TenantContext.setCurrentTenant(2L);

        assertFalse(rbac.tienePermiso(
                autenticacion, "ventas.ver"
        ));

        verify(jdbc).queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("empleado@example.test"),
                eq(2L),
                eq("ventas.ver")
        );
    }

    @Test
    void sinTenantNoDebeConcederPermiso() {

        Authentication autenticacion = empleado();

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> rbac.tienePermiso(
                        autenticacion, "ventas.ver"
                )
        );

        verifyNoInteractions(jdbc);
    }
}