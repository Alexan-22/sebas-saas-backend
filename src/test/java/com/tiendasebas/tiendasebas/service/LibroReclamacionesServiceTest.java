package com.tiendasebas.tiendasebas.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Pruebas unitarias: validaciones previas a abrir una transaccion o tocar JDBC. */
class LibroReclamacionesServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final LibroReclamacionesService service = new LibroReclamacionesService(jdbc, manager);

    private LibroReclamacionesService.Solicitud valida() {
        return new LibroReclamacionesService.Solicitud(
                "RECLAMO", "Consumidor de Prueba", "DNI", "12345678",
                "Domicilio de prueba", "prueba@example.org", null,
                false, null, "PRODUCTO", "Prenda de prueba", new BigDecimal("25.00"),
                "Producto recibido en condiciones diferentes a las indicadas",
                "Solicito una solucion conforme a la politica aplicable");
    }

    private void rechazaSinConsultarBD(LibroReclamacionesService.Solicitud solicitud) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.registrar(solicitud));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc, manager);
    }

    @AfterEach
    void limpiar() {
        com.tiendasebas.tiendasebas.config.TenantContext.clear();
    }

    @Test
    void rechazaSolicitudNula() {
        rechazaSinConsultarBD(null);
    }

    @Test
    void rechazaTipoInvalido() {
        var d = valida();
        rechazaSinConsultarBD(new LibroReclamacionesService.Solicitud(
                "OTRO", d.consumidorNombre(), d.documentoTipo(), d.documentoNumero(),
                d.domicilio(), d.correo(), d.telefono(), d.menorEdad(),
                d.representanteNombre(), d.bienTipo(), d.bienDescripcion(),
                d.montoReclamado(), d.detalle(), d.pedido()));
    }

    @Test
    void exigeMedioDeContacto() {
        var d = valida();
        rechazaSinConsultarBD(new LibroReclamacionesService.Solicitud(
                d.tipo(), d.consumidorNombre(), d.documentoTipo(), d.documentoNumero(),
                d.domicilio(), null, null, d.menorEdad(), d.representanteNombre(),
                d.bienTipo(), d.bienDescripcion(), d.montoReclamado(),
                d.detalle(), d.pedido()));
    }

    @Test
    void exigeRepresentanteSiEsMenor() {
        var d = valida();
        rechazaSinConsultarBD(new LibroReclamacionesService.Solicitud(
                d.tipo(), d.consumidorNombre(), d.documentoTipo(), d.documentoNumero(),
                d.domicilio(), d.correo(), d.telefono(), true, null,
                d.bienTipo(), d.bienDescripcion(), d.montoReclamado(),
                d.detalle(), d.pedido()));
    }

    @Test
    void rechazaMontoNegativo() {
        var d = valida();
        rechazaSinConsultarBD(new LibroReclamacionesService.Solicitud(
                d.tipo(), d.consumidorNombre(), d.documentoTipo(), d.documentoNumero(),
                d.domicilio(), d.correo(), d.telefono(), d.menorEdad(),
                d.representanteNombre(), d.bienTipo(), d.bienDescripcion(),
                new BigDecimal("-1.00"), d.detalle(), d.pedido()));
    }
}
