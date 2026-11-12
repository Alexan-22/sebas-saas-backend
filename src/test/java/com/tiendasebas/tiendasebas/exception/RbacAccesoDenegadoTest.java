package com.tiendasebas.tiendasebas.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RbacAccesoDenegadoTest {

    @RestController
    static class ControladorDePrueba {

        @GetMapping("/prueba/rbac/denegado")
        public String verificar() {
            throw new AccessDeniedException(
                "El empleado no tiene ventas.ver"
            );
        }
    }

    @Test
    void accesoDenegadoDebeResponder403() throws Exception {

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(
                get("/prueba/rbac/denegado")
                        .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Acceso denegado"));
    }
}