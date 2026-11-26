package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutInvitadoServiceTest {

    private final VentaService ventas = mock(VentaService.class);
    private final CheckoutInvitadoClienteService clientes = mock(CheckoutInvitadoClienteService.class);
    private final CheckoutInvitadoIdempotenciaService idempotencia = mock(CheckoutInvitadoIdempotenciaService.class);
    private final CheckoutInvitadoAccesoService accesos = mock(CheckoutInvitadoAccesoService.class);
    private final CheckoutInvitadoService servicio =
            new CheckoutInvitadoService(ventas, clientes, idempotencia, accesos);

    @Test
    void compraNuevaGuardaInvitadoYEntregaToken() {
        var solicitud = new CompraInvitadoRequest();
        var comprador = new ClienteInvitadoDto();
        solicitud.setCliente(comprador);
        var compra = mock(CompraResponse.class);
        when(compra.getIdVenta()).thenReturn(42);
        when(idempotencia.recuperar(solicitud)).thenReturn((Integer) null);
        when(ventas.realizarCompraInvitadoInterna(solicitud)).thenReturn(compra);
        when(accesos.generarAcceso(42)).thenReturn("token-privado-test");

        var respuesta = servicio.comprar(solicitud);

        assertSame(compra, respuesta.compra());
        assertEquals("token-privado-test", respuesta.tokenAcceso());
        var orden = inOrder(ventas, clientes, idempotencia, accesos);
        orden.verify(ventas).realizarCompraInvitadoInterna(solicitud);
        orden.verify(clientes).registrar(42, comprador);
        orden.verify(idempotencia).recordar(solicitud, 42);
        orden.verify(accesos).generarAcceso(42);
    }

    @Test
    void reintentoNoCreaVentaNiEntregaOtroToken() {
        var solicitud = new CompraInvitadoRequest();
        when(idempotencia.recuperar(solicitud)).thenReturn(42);

        var error = assertThrows(ResponseStatusException.class, () -> servicio.comprar(solicitud));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(ventas, clientes, accesos);
        verify(idempotencia, never()).recordar(any(), anyInt());
    }

    @Test
    void consultaSinTokenValidoNoExponePedido() {
        when(accesos.accesoValido(42, "incorrecto")).thenReturn(false);
        var error = assertThrows(ResponseStatusException.class,
                () -> servicio.detalle(42, "incorrecto"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(ventas, never()).obtenerDetalleVentaAdmin(anyInt());
    }

    @Test
    void consultaConTokenValidoDevuelveDetalle() {
        var detalle = mock(DetalleCompraResponse.class);
        when(accesos.accesoValido(42, "correcto")).thenReturn(true);
        when(ventas.obtenerDetalleVentaAdmin(42)).thenReturn(detalle);
        assertSame(detalle, servicio.detalle(42, "correcto"));
    }
}