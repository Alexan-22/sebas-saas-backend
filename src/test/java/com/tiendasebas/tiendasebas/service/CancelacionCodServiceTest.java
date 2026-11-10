package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.DetalleVenta;
import com.tiendasebas.tiendasebas.entity.EstadoPedido;
import com.tiendasebas.tiendasebas.entity.ProductoTalla;
import com.tiendasebas.tiendasebas.entity.TipoVenta;
import com.tiendasebas.tiendasebas.entity.Venta;
import com.tiendasebas.tiendasebas.repository.DetalleVentaRepository;
import com.tiendasebas.tiendasebas.repository.ProductoTallaRepository;
import com.tiendasebas.tiendasebas.repository.VentaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias SIN Spring Context, SIN Supabase y SIN escribir stock real.
 * Las pruebas E2E con PostgreSQL real y concurrencia van por separado.
 */
class CancelacionCodServiceTest {

    private static final Integer VENTA_ID = 85;
    private static final Long TIENDA = 1L;
    private static final String MOTIVO = "Cancelado por solicitud de prueba";

    private VentaRepository ventas;
    private DetalleVentaRepository detalles;
    private ProductoTallaRepository inventario;
    private JdbcTemplate jdbc;
    private CancelacionCodService servicio;
    private Venta venta;

    @BeforeEach
    void preparar() {
        TenantContext.setCurrentTenant(TIENDA);
        ventas = mock(VentaRepository.class);
        detalles = mock(DetalleVentaRepository.class);
        inventario = mock(ProductoTallaRepository.class);
        jdbc = mock(JdbcTemplate.class);
        servicio = new CancelacionCodService(ventas, detalles, inventario, jdbc);
        venta = new Venta();
        venta.setIdVenta(VENTA_ID);
        venta.setTiendaId(TIENDA);
        venta.setTipoVenta(TipoVenta.ONLINE);
        venta.setMedioPago("CONTRA_ENTREGA");
        venta.setEstadoPedido(EstadoPedido.RECIBIDO);
        venta.setEstado("REGISTRADA");
        when(ventas.buscarPorIdYTiendaConBloqueo(VENTA_ID, TIENDA))
                .thenReturn(Optional.of(venta));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private ResponseStatusException falla(Runnable operacion, HttpStatus esperado) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, operacion::run);
        assertEquals(esperado.value(), ex.getStatusCode().value());
        return ex;
    }

    private void esperarCobro(String estado) {
        when(jdbc.queryForList(contains("FROM public.saas_cobros_contraentrega"),
                eq(String.class), eq(TIENDA), eq(VENTA_ID))).thenReturn(List.of(estado));
    }

    private void esperarEnvio(String estado) {
        when(jdbc.queryForList(contains("FROM public.saas_envios"),
                eq(String.class), eq(TIENDA), eq(VENTA_ID))).thenReturn(List.of(estado));
    }

    @Test
    void rechazaIdInvalido() {
        falla(() -> servicio.cancelar(0, MOTIVO, "admin@test.local"), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(detalles, inventario, jdbc);
    }

    @Test
    void rechazaMotivoCorto() {
        falla(() -> servicio.cancelar(VENTA_ID, "corto", "admin@test.local"), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(detalles, inventario, jdbc);
    }

    @Test
    void rechazaSinOperadorAutenticado() {
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, " "), HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(detalles, inventario, jdbc);
    }

    @Test
    void noEncuentraVentaDeOtraTienda() {
        TenantContext.setCurrentTenant(2L);
        // El repositorio solo fue configurado para la tienda 1: prueba aislamiento por clave.
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local"), HttpStatus.NOT_FOUND);
        verify(ventas).buscarPorIdYTiendaConBloqueo(VENTA_ID, 2L);
        verifyNoInteractions(detalles, inventario, jdbc);
    }

    @Test
    void rechazaVentaQueNoEsCod() {
        venta.setMedioPago("YAPE");
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local"), HttpStatus.CONFLICT);
        verifyNoInteractions(detalles, inventario, jdbc);
    }

    @Test
    void rechazaVentaPresencial() {
        venta.setTipoVenta(TipoVenta.PRESENCIAL);
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local"), HttpStatus.CONFLICT);
        verifyNoInteractions(detalles, inventario, jdbc);
    }

    @Test
    void rechazaPedidoYaDespachadoComercialmente() {
        venta.setEstadoPedido(EstadoPedido.ENVIADO);
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local"), HttpStatus.CONFLICT);
        verifyNoInteractions(detalles, inventario, jdbc);
    }

    @Test
    void rechazaCobroYaConfirmado() {
        esperarCobro("COBRADO");
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local"), HttpStatus.CONFLICT);
        verifyNoInteractions(detalles, inventario);
    }

    @Test
    void rechazaEnvioYaDespachado() {
        esperarCobro("PENDIENTE");
        esperarEnvio("DESPACHADO");
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local"), HttpStatus.CONFLICT);
        verifyNoInteractions(detalles, inventario);
    }

    @Test
    void rechazaCancelacionPreviamenteAuditada() {
        esperarCobro("PENDIENTE");
        esperarEnvio("PENDIENTE");
        when(jdbc.queryForObject(contains("FROM public.saas_cancelaciones_cod"),
                eq(Integer.class), eq(TIENDA), eq(VENTA_ID))).thenReturn(1);
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local"), HttpStatus.CONFLICT);
        verifyNoInteractions(detalles, inventario);
    }

    @Test
    void rechazaVentaSinDetalle() {
        esperarCobro("PENDIENTE");
        esperarEnvio("PENDIENTE");
        when(jdbc.queryForObject(contains("FROM public.saas_cancelaciones_cod"),
                eq(Integer.class), eq(TIENDA), eq(VENTA_ID))).thenReturn(0);
        when(detalles.findByVentaOrderByIdDetalleVentaAsc(venta)).thenReturn(List.of());
        falla(() -> servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local"), HttpStatus.CONFLICT);
        verifyNoInteractions(inventario);
    }

    @Test
    void reponeLaVarianteYMarcaLaVentaCanceladaEnFlujoSimulado() {
        esperarCobro("PENDIENTE");
        esperarEnvio("PREPARANDO");
        when(jdbc.queryForObject(contains("FROM public.saas_cancelaciones_cod"),
                eq(Integer.class), eq(TIENDA), eq(VENTA_ID))).thenReturn(0);

        ProductoTalla variante = new ProductoTalla();
        variante.setIdProductoTalla(15);
        variante.setStock(18);
        DetalleVenta detalle = new DetalleVenta();
        detalle.setProductoTalla(variante);
        detalle.setCantidad(2);
        when(detalles.findByVentaOrderByIdDetalleVentaAsc(venta)).thenReturn(List.of(detalle));
        when(inventario.buscarPorIdConBloqueo(15, TIENDA)).thenReturn(Optional.of(variante));
        // No se conecta a PostgreSQL: simulamos los INSERT/UPDATE exitosos.
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        CancelacionCodService.Resultado resultado =
                servicio.cancelar(VENTA_ID, MOTIVO, "admin@test.local");

        assertEquals(2, resultado.unidadesRepuestas());
        assertEquals("CANCELADO", resultado.estadoPedido());
        assertEquals("CANCELADO", resultado.estadoCobro());
        assertEquals("CANCELADO", resultado.estadoEnvio());
        assertEquals(20, variante.getStock());
        assertEquals(EstadoPedido.CANCELADO, venta.getEstadoPedido());
        assertEquals("CANCELADA", venta.getEstado());
        verify(inventario, times(1)).save(variante);
        verify(ventas, times(1)).saveAndFlush(venta);
    }
}
