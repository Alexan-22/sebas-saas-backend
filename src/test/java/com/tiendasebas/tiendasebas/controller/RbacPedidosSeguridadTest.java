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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RbacPedidosSeguridadTest {

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
        RbacPedidosEmpleadoController controlador(VentaService ventas) {
            return new RbacPedidosEmpleadoController(ventas);
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
                        contexto.getBean(RbacPedidosEmpleadoController.class)
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

    private void autenticar(String rol, boolean permiso) {
        var autenticacion =
                new UsernamePasswordAuthenticationToken(
                        "empleado@example.test",
                        null,
                        List.of(
                            new SimpleGrantedAuthority("ROLE_" + rol)
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(autenticacion);

        when(rbac.tienePermiso(
                any(), eq("pedidos.gestionar")
        )).thenReturn(permiso);
    }

    private void ejecutarEstado(String estado, int codigo)
            throws Exception {

        mvc.perform(
                put("/api/rbac/empleado/pedidos/10/estado")
                        .contentType("application/json")
                        .content(
                                "{\"estadoPedido\":\"" + estado + "\"}"
                        )
        ).andExpect(status().is(codigo));
    }

    @Test
    void empleadoPuedeSolicitarPreparacion() throws Exception {
        autenticar("EMPLEADO", true);

        ejecutarEstado("EN_PREPARACION", 200);

        verify(ventas).actualizarEstadoPedido(
                10, "EN_PREPARACION"
        );
    }

    @Test
    void empleadoPuedeSolicitarEnvio() throws Exception {
        autenticar("EMPLEADO", true);

        ejecutarEstado("ENVIADO", 200);

        verify(ventas).actualizarEstadoPedido(
                10, "ENVIADO"
        );
    }

    @Test
    void empleadoPuedeSolicitarEntrega() throws Exception {
        autenticar("EMPLEADO", true);

        ejecutarEstado("ENTREGADO", 200);

        verify(ventas).actualizarEstadoPedido(
                10, "ENTREGADO"
        );
    }

    @Test
    void empleadoSinPermisoRecibe403() throws Exception {
        autenticar("EMPLEADO", false);

        ejecutarEstado("EN_PREPARACION", 403);

        verifyNoInteractions(ventas);
    }

    @Test
    void adminNoUtilizaRutaDeEmpleado() throws Exception {
        autenticar("ADMIN", true);

        ejecutarEstado("EN_PREPARACION", 403);

        verifyNoInteractions(ventas);
    }

    @Test
    void clienteNoGestionaPedidos() throws Exception {
        autenticar("CLIENTE", true);

        ejecutarEstado("ENVIADO", 403);

        verifyNoInteractions(ventas);
    }

    @Test
    void empleadoNoPuedeCancelarPedido() throws Exception {
        autenticar("EMPLEADO", true);

        ejecutarEstado("CANCELADO", 400);

        verifyNoInteractions(ventas);
    }
}