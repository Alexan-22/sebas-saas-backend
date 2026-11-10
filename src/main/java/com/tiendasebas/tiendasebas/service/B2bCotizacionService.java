package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Simulador puro de cotizacion: NO crea ventas, NO reserva ni descuenta stock. */
@Service
public class B2bCotizacionService {
    private final JdbcTemplate db;
    private final B2bService configuracion;

    public B2bCotizacionService(JdbcTemplate db, B2bService configuracion) {
        this.db = db;
        this.configuracion = configuracion;
    }

    public record ItemSolicitud(Integer idProducto, Integer cantidad) {}
    public record Solicitud(List<ItemSolicitud> items) {}
    public record Linea(Integer idProducto, String nombre, int cantidad,
                        BigDecimal precioBase, BigDecimal precioAplicado,
                        BigDecimal subtotalBase, BigDecimal subtotalCotizado,
                        BigDecimal ahorro, boolean escalaAplicada) {}
    public record Cotizacion(boolean b2bHabilitado, boolean cumpleMinimos,
                             int unidades, int minimoPrendas, BigDecimal minimoMonto,
                             int faltanPrendas, BigDecimal faltanMonto,
                             BigDecimal subtotalBase, BigDecimal totalCotizado,
                             BigDecimal ahorroEstimado, List<Linea> lineas,
                             String aviso) {}
    private record PrecioProducto(Integer idProducto, String nombre, BigDecimal precio,
                                  BigDecimal precioOferta) {}

    @Transactional(readOnly = true)
    public Cotizacion cotizar(Solicitud solicitud) {
        Long tiendaId = TenantContext.getCurrentTenant();
        if (tiendaId == null || tiendaId <= 0L) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tienda no identificada");
        }
        if (solicitud == null || solicitud.items() == null || solicitud.items().isEmpty()
                || solicitud.items().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envia de 1 a 100 lineas");
        }

        // Agrupar IDs repetidos: no se puede eludir una escala dividiendo el mismo producto.
        Map<Integer,Integer> cantidades = new LinkedHashMap<>();
        int unidades = 0;
        for (ItemSolicitud item : solicitud.items()) {
            if (item == null || item.idProducto() == null || item.idProducto() <= 0
                    || item.cantidad() == null || item.cantidad() <= 0 || item.cantidad() > 10000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Producto o cantidad invalida (maximo 10000 por linea)");
            }
            unidades += item.cantidad();
            if (unidades > 100000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cantidad total fuera de rango");
            }
            cantidades.merge(item.idProducto(), item.cantidad(), Integer::sum);
        }

        B2bService.Config config = configuracion.obtenerConfig();
        boolean habilitado = config.habilitado();
        BigDecimal totalBase = BigDecimal.ZERO.setScale(2);
        BigDecimal totalCotizado = BigDecimal.ZERO.setScale(2);
        List<Linea> lineas = new ArrayList<>();

        for (Map.Entry<Integer,Integer> item : cantidades.entrySet()) {
            int id = item.getKey();
            int cantidad = item.getValue();
            // La clausula tienda_id impide cotizar un producto de otro tenant.
            List<PrecioProducto> productos = db.query("""
                SELECT id_producto, nombre, precio, precio_oferta
                FROM public.productos
                WHERE tienda_id = ? AND id_producto = ? AND estado IS TRUE
                """, (rs, n) -> new PrecioProducto(rs.getInt("id_producto"),
                    rs.getString("nombre"), rs.getBigDecimal("precio"),
                    rs.getBigDecimal("precio_oferta")), tiendaId, id);
            if (productos.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto no disponible en esta tienda");
            }
            PrecioProducto p = productos.get(0);
            if (p.precio() == null || p.precio().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Producto sin precio valido");
            }
            BigDecimal base = p.precio();
            if (p.precioOferta() != null && p.precioOferta().signum() > 0
                    && p.precioOferta().compareTo(base) < 0) {
                base = p.precioOferta();
            }
            base = base.setScale(2, RoundingMode.HALF_UP);
            BigDecimal aplicado = base;

            if (habilitado) {
                List<BigDecimal> escalas = db.query("""
                    SELECT precio_unitario
                    FROM public.saas_b2b_escalas
                    WHERE tienda_id = ? AND id_producto = ? AND cantidad_minima <= ?
                    ORDER BY cantidad_minima DESC LIMIT 1
                    """, (rs, n) -> rs.getBigDecimal(1), tiendaId, id, cantidad);
                if (!escalas.isEmpty() && escalas.get(0) != null
                        && escalas.get(0).signum() > 0
                        && escalas.get(0).compareTo(base) < 0) {
                    aplicado = escalas.get(0).setScale(2, RoundingMode.HALF_UP);
                }
            }
            BigDecimal cantidadBD = BigDecimal.valueOf(cantidad);
            BigDecimal parcialBase = base.multiply(cantidadBD).setScale(2);
            BigDecimal parcialCotizado = aplicado.multiply(cantidadBD).setScale(2);
            BigDecimal ahorro = parcialBase.subtract(parcialCotizado);
            totalBase = totalBase.add(parcialBase);
            totalCotizado = totalCotizado.add(parcialCotizado);
            lineas.add(new Linea(p.idProducto(), p.nombre(), cantidad, base, aplicado,
                    parcialBase, parcialCotizado, ahorro, aplicado.compareTo(base) < 0));
        }

        int minimoPrendas = habilitado ? config.minimoPrendas() : 0;
        BigDecimal minimoMonto = habilitado ? config.minimoMonto() : BigDecimal.ZERO;
        int faltanPrendas = Math.max(0, minimoPrendas - unidades);
        BigDecimal faltanMonto = minimoMonto.subtract(totalCotizado).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        boolean cumple = faltanPrendas == 0 && faltanMonto.signum() == 0;

        return new Cotizacion(habilitado, cumple, unidades, minimoPrendas, minimoMonto,
                faltanPrendas, faltanMonto, totalBase, totalCotizado,
                totalBase.subtract(totalCotizado), List.copyOf(lineas),
                "SIMULACION: no confirma precio de compra, disponibilidad, reserva de stock ni cobro."
        );
    }
}
