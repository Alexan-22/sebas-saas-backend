package com.tiendasebas.tiendasebas.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ConstanciaReclamacionesDocumentoServiceTest {
    private final ConstanciaReclamacionesDocumentoService servicio = new ConstanciaReclamacionesDocumentoService();
    private final ConstanciaReclamacionesDocumentoService.Proveedor proveedor =
            new ConstanciaReclamacionesDocumentoService.Proveedor("Comercio de prueba", "20123456789", "Dirección ficticia");

    private ConstanciaReclamacionesService.Hoja hoja(String detalle) {
        return new ConstanciaReclamacionesService.Hoja(7L,
                OffsetDateTime.parse("2026-11-24T15:10:00Z"), "RECLAMO", "Persona Ficticia",
                "DNI", "00000000", "Domicilio de prueba", "prueba@example.test", null,
                false, null, "PRODUCTO", "Prenda de prueba", new BigDecimal("25.50"),
                detalle, "Cambio solicitado");
    }

    @Test void generaConstanciaDesdeHojaConFechaDeLima() {
        String html = servicio.generarHtml(hoja("Descripción ficticia"), proveedor);
        assertTrue(html.contains("Hoja N.º 7"));
        assertTrue(html.contains("24/11/2026 10:10 PET"));
        assertTrue(html.contains("Persona Ficticia"));
        assertTrue(html.contains("S/ 25.50"));
        assertTrue(html.contains("Comercio de prueba"));
    }

    @Test void escapaHtmlDelConsumidor() {
        String html = servicio.generarHtml(hoja("<script>alert('x')</script> & resto"), proveedor);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; resto"));
    }

    @Test void rechazaProveedorSinDatosLegales() {
        assertThrows(IllegalArgumentException.class, () ->
                servicio.generarHtml(hoja("Detalle"), new ConstanciaReclamacionesDocumentoService.Proveedor("", "", "")));
        assertThrows(IllegalArgumentException.class, () ->
                servicio.generarHtml(hoja("Detalle"), new ConstanciaReclamacionesDocumentoService.Proveedor("Prueba", "123", "Dirección")));
    }

    @Test void rechazaHojaAusente() {
        assertThrows(IllegalArgumentException.class, () -> servicio.generarHtml(null, proveedor));
    }
}
