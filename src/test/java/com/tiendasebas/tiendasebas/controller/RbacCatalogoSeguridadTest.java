package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.exception.GlobalExceptionHandler;
import com.tiendasebas.tiendasebas.service.ProductoService;
import com.tiendasebas.tiendasebas.service.ProductoTallaService;
import com.tiendasebas.tiendasebas.service.RbacAutorizacionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RbacCatalogoSeguridadTest {

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Configuracion {

        @Bean
        ProductoService productoService() {
            return mock(ProductoService.class);
        }

        @Bean
        ProductoTallaService productoTallaService() {
            return mock(ProductoTallaService.class);
        }

        @Bean("rbacAutorizacionService")
        RbacAutorizacionService rbacAutorizacionService() {
            return mock(RbacAutorizacionService.class);
        }

        @Bean
        RbacCatalogoEmpleadoController controlador(
                ProductoService productos,
                ProductoTallaService inventario
        ) {
            return new RbacCatalogoEmpleadoController(
                    productos, inventario
            );
        }
    }

    private AnnotationConfigApplicationContext contexto;
    private MockMvc mvc;
    private RbacAutorizacionService rbac;

    @BeforeEach
    void preparar() {
        contexto = new AnnotationConfigApplicationContext(
                Configuracion.class
        );

        rbac = contexto.getBean(RbacAutorizacionService.class);

        mvc = MockMvcBuilders
                .standaloneSetup(
                    contexto.getBean(RbacCatalogoEmpleadoController.class)
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
    void empleadoConPermisoPuedeVerProductos() throws Exception {
        autenticar("EMPLEADO");

        when(rbac.tienePermiso(
                any(), eq("productos.ver")
        )).thenReturn(true);

        mvc.perform(get("/api/rbac/empleado/productos"))
                .andExpect(status().isOk());
    }

    @Test
    void empleadoSinPermisoNoPuedeVerProductos() throws Exception {
        autenticar("EMPLEADO");

        when(rbac.tienePermiso(
                any(), eq("productos.ver")
        )).thenReturn(false);

        mvc.perform(get("/api/rbac/empleado/productos"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminNoPuedeUsarEndpointDeEmpleado() throws Exception {
        autenticar("ADMIN");

        when(rbac.tienePermiso(
                any(), eq("productos.ver")
        )).thenReturn(true);

        mvc.perform(get("/api/rbac/empleado/productos"))
                .andExpect(status().isForbidden());
    }

    @Test
    void empleadoConPermisoPuedeVerStock() throws Exception {
        autenticar("EMPLEADO");

        when(rbac.tienePermiso(
                any(), eq("stock.ver")
        )).thenReturn(true);

        mvc.perform(get("/api/rbac/empleado/stock"))
                .andExpect(status().isOk());
    }

    @Test
    void empleadoSinPermisoNoPuedeVerStock() throws Exception {
        autenticar("EMPLEADO");

        when(rbac.tienePermiso(
                any(), eq("stock.ver")
        )).thenReturn(false);

        mvc.perform(get("/api/rbac/empleado/stock"))
                .andExpect(status().isForbidden());
    }
}