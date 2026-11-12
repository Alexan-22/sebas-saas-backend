package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class VarianteAdminService {

    private final JdbcTemplate jdbc;

    public VarianteAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NuevaVariante(
            Integer idProducto,
            Integer idTalla,
            Long idColor,
            Integer stock
    ) {}

    public record Variante(
            Integer idProductoTalla,
            Integer idProducto,
            Integer idTalla,
            Long idColor,
            String talla,
            String color,
            Integer stock
    ) {}

    private ResponseStatusException error(
            HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    private int contar(String sql, Object... parametros) {
        Integer cantidad = jdbc.queryForObject(
                sql, Integer.class, parametros);
        return cantidad == null ? 0 : cantidad;
    }

    @Transactional(readOnly = true)
    public List<Variante> listar(Integer idProducto) {

        Long tiendaId = TenantContext.getCurrentTenant();

        if (idProducto == null || contar("""
                SELECT COUNT(*)
                FROM public.productos
                WHERE id_producto = ? AND tienda_id = ?
                """, idProducto, tiendaId) == 0) {
            throw error(HttpStatus.NOT_FOUND,
                    "Producto no encontrado");
        }

        return jdbc.query("""
                SELECT
                    pt.id_producto_talla,
                    pt.id_producto,
                    pt.id_talla,
                    pt.id_color,
                    t.nombre_talla,
                    c.nombre AS nombre_color,
                    pt.stock
                FROM public.producto_tallas pt
                JOIN public.productos p
                  ON p.id_producto = pt.id_producto
                LEFT JOIN public.tallas t
                  ON t.id_talla = pt.id_talla
                LEFT JOIN public.saas_colores_tienda c
                  ON c.id_color = pt.id_color
                WHERE pt.id_producto = ?
                  AND p.tienda_id = ?
                ORDER BY pt.id_producto_talla
                """, (rs, fila) -> new Variante(
                    rs.getInt("id_producto_talla"),
                    rs.getInt("id_producto"),
                    rs.getObject("id_talla", Integer.class),
                    rs.getObject("id_color", Long.class),
                    rs.getString("nombre_talla"),
                    rs.getString("nombre_color"),
                    rs.getInt("stock")
                ), idProducto, tiendaId);
    }

    @Transactional
    public Integer crear(NuevaVariante solicitud) {

        Long tiendaId = TenantContext.getCurrentTenant();

        if (solicitud == null ||
                solicitud.idProducto() == null ||
                solicitud.stock() == null ||
                solicitud.stock() < 0) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Producto y stock valido son obligatorios");
        }

        Integer idProducto = solicitud.idProducto();
        Integer idTalla = solicitud.idTalla();
        Long idColor = solicitud.idColor();

        List<String> tipos = jdbc.query("""
                SELECT tipo_variantes
                FROM public.productos
                WHERE id_producto = ? AND tienda_id = ?
                """, (rs, fila) -> rs.getString(1),
                idProducto, tiendaId);

        if (tipos.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND,
                    "Producto no encontrado");
        }

        String tipo = tipos.get(0);

        if (tipo == null || tipo.isBlank()) {
            throw error(HttpStatus.CONFLICT,
                    "Primero configure el tipo de variantes del producto");
        }

        boolean requiereColor =
                "SOLO_COLOR".equals(tipo) ||
                "COLOR_Y_TALLA".equals(tipo);

        boolean requiereTalla =
                "SOLO_TALLA".equals(tipo) ||
                "COLOR_Y_TALLA".equals(tipo);

        if (!requiereColor && !requiereTalla &&
                !"SIN_VARIANTES".equals(tipo)) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Tipo de variantes desconocido");
        }

        if (requiereColor != (idColor != null) ||
                requiereTalla != (idTalla != null)) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Color y talla no coinciden con el tipo de producto");
        }

        if (idTalla != null && contar("""
                SELECT COUNT(*) FROM public.tallas
                WHERE id_talla = ? AND estado = TRUE
                """, idTalla) == 0) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Talla inexistente o inactiva");
        }

        if (idColor != null && contar("""
                SELECT COUNT(*)
                FROM public.saas_colores_tienda
                WHERE id_color = ?
                  AND tienda_id = ?
                  AND activo = TRUE
                """, idColor, tiendaId) == 0) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Color inexistente o ajeno a la tienda");
        }

        // No mezclar stock antiguo sin color con uno nuevo
        // sin realizar previamente una migracion controlada.
        if (idColor != null && contar("""
                SELECT COUNT(*)
                FROM public.producto_tallas
                WHERE id_producto = ?
                  AND id_color IS NULL
                """, idProducto) > 0) {
            throw error(HttpStatus.CONFLICT,
                    "Hay inventario anterior sin color. Requiere migracion");
        }

        try {
            return jdbc.queryForObject("""
                    INSERT INTO public.producto_tallas
                        (id_producto, id_talla, id_color, stock)
                    VALUES (?, ?, ?, ?)
                    RETURNING id_producto_talla
                    """, Integer.class,
                    idProducto, idTalla, idColor, solicitud.stock());

        } catch (DuplicateKeyException ex) {
            throw error(HttpStatus.CONFLICT,
                    "Ya existe esta combinacion de color y talla");
        }
    }
}