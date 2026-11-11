package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RbacAutorizacionServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RbacAutorizacionService servicio =
            new RbacAutorizacionService(jdbc);

    private UsernamePasswordAuthenticationToken empleado() {
        return new UsernamePasswordAuthenticationToken(
                "empleado@example.test",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLEADO"))
        );
    }

    @AfterEach
    void limpiarTenant() {
        TenantContext.clear();
    }

    @Test
    void empleadoSinPermisoDebeSerRechazado() {
        TenantContext.setCurrentTenant(1L);

        when(jdbc.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("empleado@example.test"),
                eq(1L),
                eq("ventas.ver")
        )).thenReturn(false);

        boolean permitido = servicio.tienePermiso(
                empleado(), "ventas.ver"
        );

        assertFalse(permitido);
    }

    @Test
    void empleadoConPermisoDebeSerAceptado() {
        TenantContext.setCurrentTenant(1L);

        when(jdbc.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("empleado@example.test"),
                eq(1L),
                eq("ventas.ver")
        )).thenReturn(true);

        boolean permitido = servicio.tienePermiso(
                empleado(), "ventas.ver"
        );

        assertTrue(permitido);
    }

    @Test
    void consultaDebeUtilizarTenantActual() {
        TenantContext.setCurrentTenant(2L);

        when(jdbc.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("empleado@example.test"),
                eq(2L),
                eq("ventas.ver")
        )).thenReturn(false);

        assertFalse(servicio.tienePermiso(
                empleado(), "ventas.ver"
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
    void autenticacionAusenteDebeRechazarse() {
        TenantContext.setCurrentTenant(1L);

        assertFalse(servicio.tienePermiso(
                null, "ventas.ver"
        ));

        verifyNoInteractions(jdbc);
    }
}