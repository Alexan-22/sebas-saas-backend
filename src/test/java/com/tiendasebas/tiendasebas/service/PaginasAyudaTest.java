package com.tiendasebas.tiendasebas.service;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaginasAyudaTest {

    @Test
    void configuracionAntiguaNoMuestraPaginas() {
        assertTrue(PaginasAyuda.validar(null).isEmpty());
    }

    @Test
    void permitePaginaOcultaSinContenido() {
        var pagina = new PaginasAyuda.Pagina(
            PaginasAyuda.Clave.CAMBIOS, "", "", false
        );

        assertEquals(1,
            PaginasAyuda.validar(List.of(pagina)).size());
    }

    @Test
    void paginaVisibleRequiereContenido() {
        var pagina = new PaginasAyuda.Pagina(
            PaginasAyuda.Clave.TERMINOS,
            "Terminos y condiciones",
            "",
            true
        );

        assertThrows(IllegalArgumentException.class,
            () -> PaginasAyuda.validar(List.of(pagina)));
    }

    @Test
    void rechazaPaginasDuplicadas() {
        var a = new PaginasAyuda.Pagina(
            PaginasAyuda.Clave.ENVIOS, "Envios", "Texto A", true
        );

        var b = new PaginasAyuda.Pagina(
            PaginasAyuda.Clave.ENVIOS, "Envios", "Texto B", true
        );

        assertThrows(IllegalArgumentException.class,
            () -> PaginasAyuda.validar(List.of(a, b)));
    }

    @Test
    void normalizaTextos() {
        var pagina = new PaginasAyuda.Pagina(
            PaginasAyuda.Clave.CAMBIOS,
            " Cambios ",
            " Contenido de la tienda ",
            true
        );

        var resultado = PaginasAyuda.validar(
            List.of(pagina)
        );

        assertEquals("Cambios", resultado.get(0).titulo());
        assertEquals(
            "Contenido de la tienda",
            resultado.get(0).contenido()
        );
    }
}