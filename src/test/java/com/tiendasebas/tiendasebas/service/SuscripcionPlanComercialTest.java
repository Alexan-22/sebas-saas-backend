package com.tiendasebas.tiendasebas.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuscripcionPlanComercialTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlanComercialService servicio = new PlanComercialService(jdbc, mock(SaasActividadService.class));

    @Test
    void exigeSeleccionCuandoHayVariosPlanesActivos() {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(0L), eq(0L)))
                .thenReturn(List.of(4L, 9L));

        assertNull(servicio.planComercial());
    }

    @Test
    void devuelveElPlanActivoConfigurado() {
        ReflectionTestUtils.setField(servicio, "planComercialId", 9L);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(9L), eq(9L)))
                .thenReturn(List.of(9L));

        assertEquals(9L, servicio.planComercial());
    }
}
