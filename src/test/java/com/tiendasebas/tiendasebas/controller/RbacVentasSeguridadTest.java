package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.exception.GlobalExceptionHandler;
import com.tiendasebas.tiendasebas.service.RbacAutorizacionService;
import com.tiendasebas.tiendasebas.service.VentaService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RbacVentasSeguridadTest {

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Configuracion {

        @Bean
        VentaService ventaService() {
            return mock(VentaService.class);
        }

        @Bean("rbacAutorizacionService")
        RbacAutorizacionService rbacAutorizacionService() {
            return mock(RbacAutorizacionService.class);
        }

        @Bean
        RbacVentasEmpleadoController controlador(
                VentaService ventas
        ) {
            return new RbacVentasEmpleadoController(ventas);
        }
    }

    private AnnotationConfigApplicationContext contexto;
    private MockMvc mvc;
    private RbacAutorizacionService rbac;
    private VentaService ventas;

    @BeforeEach
    void preparar() {
        contexto = new AnnotationConfigApplicationContext(
                Configuracion.class
        );

        rbac = contexto.getBean(RbacAutorizacionService.class);
        ventas = contexto.getBean(VentaService.class);

        mvc = MockMvcBuilders
                .standaloneSetup(
                    contexto.getBean(RbacVentasEmpleadoController.class)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();

        if (contexto != null) {
            contexto.close();
        }
    }

    private void autenticar(String rol) {
        var autenticacion =
                new UsernamePasswordAuthenticationToken(
                    "prueba@example.test",
                    null,
                    List.of(
                        new SimpleGrantedAuthority("ROLE_" + rol)
                    )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(autenticacion);
    }

    @Test
    void empleadoConPermisoPuedeListarVentas() throws Exception {
        autenticar("EMPLEADO");

        when(rbac.tienePermiso(
                any(), eq("ventas.ver")
        )).thenReturn(true);

        when(ventas.listarTodas()).thenReturn(List.of());

        mvc.perform(get("/api/rbac/empleado/ventas"))
                .andExpect(status().isOk());

        verify(ventas).listarTodas();
    }

    @Test
    void empleadoSinPermisoNoPuedeListarVentas() throws Exception {
        autenticar("EMPLEADO");

        when(rbac.tienePermiso(
                any(), eq("ventas.ver")
        )).thenReturn(false);

        mvc.perform(get("/api/rbac/empleado/ventas"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ventas);
    }

    @Test
    void adminNoPuedeUsarEndpointDeEmpleado() throws Exception {
        autenticar("ADMIN");

        mvc.perform(get("/api/rbac/empleado/ventas"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ventas);
    }

    @Test
    void empleadoConPermisoPuedeConsultarDetalle() throws Exception {
        autenticar("EMPLEADO");

        when(rbac.tienePermiso(
                any(), eq("ventas.ver")
        )).thenReturn(true);

        mvc.perform(
                get("/api/rbac/empleado/ventas/10/detalle")
        ).andExpect(status().isOk());

        verify(ventas).obtenerDetalleVentaAdmin(10);
    }

    @Test
    void empleadoSinPermisoNoPuedeConsultarDetalle() throws Exception {
        autenticar("EMPLEADO");

        when(rbac.tienePermiso(
                any(), eq("ventas.ver")
        )).thenReturn(false);

        mvc.perform(
                get("/api/rbac/empleado/ventas/10/detalle")
        ).andExpect(status().isForbidden());

        verifyNoInteractions(ventas);
    }

    @Test
    void clienteNoPuedeConsultarVentasDeEmpleados() throws Exception {
        autenticar("CLIENTE");

        mvc.perform(get("/api/rbac/empleado/ventas"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ventas);
    }
}