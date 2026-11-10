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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fase 3D: cancelacion COD pendiente + reposicion atomica UNA SOLA VEZ.
 * Solo ADMIN, con tenant y bloqueo pessimista; NO reembolsa pagos.
 */
@Service
public class CancelacionCodService {

    public record Resultado(Integer idVenta, String estadoPedido,
                            String estadoCobro, String estadoEnvio,
                            int unidadesRepuestas) {}

    private final VentaRepository ventas;
    private final DetalleVentaRepository detalles;
    private final ProductoTallaRepository inventario;
    private final JdbcTemplate jdbc;

    public CancelacionCodService(VentaRepository ventas,
                                DetalleVentaRepository detalles,
                                ProductoTallaRepository inventario,
                                JdbcTemplate jdbc) {
        this.ventas = ventas;
        this.detalles = detalles;
        this.inventario = inventario;
        this.jdbc = jdbc;
    }

    @Transactional
    public Resultado cancelar(Integer idVenta, String motivo, String operador) {
        Long tienda = TenantContext.getCurrentTenant();
        if (tienda == null || idVenta == null || idVenta <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pedido no valido");
        }
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < 10 || razon.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Motivo obligatorio entre 10 y 300 caracteres");
        }
        if (operador == null || operador.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        // Lock de venta por tienda. Cualquier otro cambio de estado comercial
        // debe usar este mismo metodo con bloqueo (VentaService).
        Venta venta = ventas.buscarPorIdYTiendaConBloqueo(idVenta, tienda)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (venta.getTipoVenta() != TipoVenta.ONLINE ||
                !"CONTRA_ENTREGA".equals(venta.getMedioPago())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden cancelar pedidos ONLINE contra entrega pendientes");
        }
        if (venta.getEstadoPedido() != EstadoPedido.RECIBIDO &&
                venta.getEstadoPedido() != EstadoPedido.EN_PREPARACION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El pedido ya cambio de estado y no puede cancelarse por este flujo");
        }

        // El candado del cobro evita carreras con el boton Confirmar cobro.
        List<String> cobros = jdbc.queryForList("""
                SELECT estado FROM public.saas_cobros_contraentrega
                 WHERE tienda_id=? AND id_venta=? FOR UPDATE
                """, String.class, tienda, idVenta);
        if (cobros.size() != 1 || !"PENDIENTE".equals(cobros.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede cancelar un cobro ausente, cobrado o ya procesado");
        }

        // Lock de reparto: no cancelar pedidos que ya salieron de la tienda.
        List<String> envios = jdbc.queryForList("""
                SELECT estado FROM public.saas_envios
                 WHERE tienda_id=? AND id_venta=? AND operador='PROPIO'
                   AND modalidad='DOMICILIO' FOR UPDATE
                """, String.class, tienda, idVenta);
        if (envios.size() != 1 ||
                !("PENDIENTE".equals(envios.get(0)) || "PREPARANDO".equals(envios.get(0)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El reparto ya fue despachado o no permite cancelacion simple");
        }

        // Para evitar reposicion duplicada incluso ante un error de programacion.
        Integer previas = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.saas_cancelaciones_cod
                 WHERE tienda_id=? AND id_venta=?
                """, Integer.class, tienda, idVenta);
        if (previas == null || previas != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido ya cancelado");
        }

        List<DetalleVenta> renglones = detalles.findByVentaOrderByIdDetalleVentaAsc(venta);
        if (renglones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Venta sin detalle; requiere revision manual");
        }
        // Reunir por variante, ordenar ID, luego bloquear todas las variantes.
        // Misma secuencia de ID que los procesos de venta para limitar deadlocks.
        Map<Integer, Integer> porVariante = new TreeMap<>();
        int unidades = 0;
        for (DetalleVenta d : renglones) {
            Integer cantidad = d.getCantidad();
            if (d.getProductoTalla() == null || d.getProductoTalla().getIdProductoTalla() == null ||
                    cantidad == null || cantidad <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Detalle invalido para reposicion");
            }
            int idVariante = d.getProductoTalla().getIdProductoTalla();
            try {
                porVariante.merge(idVariante, cantidad, Math::addExact);
                unidades = Math.addExact(unidades, cantidad);
            } catch (ArithmeticException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cantidad excede limite de inventario");
            }
        }

        // Primero la bitacora con UNIQUE(tienda_id,id_venta); si la transaccion
        // falla en cualquier paso, se revierte junto con todos los stocks.
        int bitacora = jdbc.update("""
                INSERT INTO public.saas_cancelaciones_cod
                  (tienda_id, id_venta, motivo, cancelado_por, unidades_repuestas)
                VALUES (?,?,?,?,?) ON CONFLICT (tienda_id,id_venta) DO NOTHING
                """, tienda, idVenta, razon, operador, unidades);
        if (bitacora != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reposicion ya registrada");
        }

        for (Map.Entry<Integer,Integer> item : porVariante.entrySet()) {
            ProductoTalla variante = inventario.buscarPorIdConBloqueo(item.getKey(), tienda)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Variante del pedido no encontrada en la tienda"));
            Integer stockActual = variante.getStock();
            if (stockActual == null || stockActual < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock actual invalido");
            }
            try {
                variante.setStock(Math.addExact(stockActual, item.getValue()));
            } catch (ArithmeticException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock excede el maximo permitido");
            }
            inventario.save(variante);
            // Ejecutar UPDATE antes de auditar; un fallo de kardex revierte ambos.
            inventario.flush();
            int movimiento = jdbc.update("""
                    INSERT INTO public.inventario_kardex
                      (tienda_id, id_venta, id_producto_talla, tipo, origen, cantidad,
                       stock_anterior, stock_posterior, motivo, operador)
                    VALUES (?,?,?,'ENTRADA','CANCELACION_COD',?,?,?,?,?)
                    """, tienda, idVenta, item.getKey(), item.getValue(),
                    stockActual, variante.getStock(), razon, operador);
            if (movimiento != 1) {
                throw new IllegalStateException("No se registro la reposicion en kardex");
            }
        }

        int estadoCobro = jdbc.update("""
                UPDATE public.saas_cobros_contraentrega
                   SET estado='CANCELADO', actualizado_en=now()
                 WHERE tienda_id=? AND id_venta=? AND estado='PENDIENTE'
                """, tienda, idVenta);
        int estadoEnvio = jdbc.update("""
                UPDATE public.saas_envios
                   SET estado='CANCELADO', actualizado_en=now()
                 WHERE tienda_id=? AND id_venta=? AND estado IN ('PENDIENTE','PREPARANDO')
                """, tienda, idVenta);
        if (estadoCobro != 1 || estadoEnvio != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El estado de pago o reparto cambio durante la cancelacion");
        }
        venta.setEstadoPedido(EstadoPedido.CANCELADO);
        venta.setEstado("CANCELADA");
        ventas.saveAndFlush(venta);
        return new Resultado(idVenta, "CANCELADO", "CANCELADO", "CANCELADO", unidades);
    }

    /** Defensa adicional para la ruta comercial PUT /admin/{id}/estado. */
    @Transactional(readOnly = true)
    public void validarEstadoComercialCod(Venta venta, EstadoPedido nuevo) {
        if (venta.getTipoVenta() != TipoVenta.ONLINE ||
                !"CONTRA_ENTREGA".equals(venta.getMedioPago())) return;
        Long tienda = TenantContext.getCurrentTenant();
        List<String> cobros = jdbc.queryForList("""
                SELECT estado FROM public.saas_cobros_contraentrega
                 WHERE tienda_id=? AND id_venta=?
                """, String.class, tienda, venta.getIdVenta());
        if (cobros.size() != 1 || "CANCELADO".equals(cobros.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cobro COD ausente o cancelado");
        }
        if (nuevo == EstadoPedido.ENTREGADO && !"COBRADO".equals(cobros.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede marcar entregado mientras el pago COD no esta cobrado");
        }
    }
}
