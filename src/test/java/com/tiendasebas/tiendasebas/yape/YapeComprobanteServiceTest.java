package com.tiendasebas.tiendasebas.yape;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class YapeComprobanteServiceTest {
    private final YapeComprobanteService service = new YapeComprobanteService(mock(JdbcTemplate.class));

    @Test
    void rechazaMasDeCincoMegabytesAntesDeConsultarBase() {
        byte[] contenido = new byte[5 * 1024 * 1024 + 1];
        var archivo = new MockMultipartFile("archivo", "captura.png", "image/png", contenido);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.subir(1, "cliente0@example.test", archivo));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rechazaHtmlRenombradoComoImagen() {
        var archivo = new MockMultipartFile("archivo", "captura.png", "image/png",
                "<html>falso</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.subir(1, "cliente0@example.test", archivo));
        assertEquals(400, error.getStatusCode().value());
    }
}
