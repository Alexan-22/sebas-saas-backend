package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tiendas/admin/variantes")
public class QuitarColorProductoController {
    private final JdbcTemplate jdbc;

    public QuitarColorProductoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Resultado(int variantesQuitadas, String mensaje) {}

    @DeleteMapping("/producto/{idProducto}/color/{idColor}")
    @Transactional
    public Resultado quitar(@PathVariable Integer idProducto,
                            @PathVariable Long idColor) {
        Long tiendaId = TenantContext.getCurrentTenant();
        if (idProducto == null || idProducto <= 0 || idColor == null || idColor <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificadores invalidos");
        }
        // Bloquear el producto antes de examinar sus variantes. No modifica el catalogo global.
        List<Integer> productos = jdbc.queryForList("""
                SELECT id_producto FROM public.productos
                WHERE id_producto = ? AND tienda_id = ? AND tipo_variantes = 'COLOR_Y_TALLA'
                FOR UPDATE
                """, Integer.class, idProducto, tiendaId);
        if (productos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Producto no encontrado o no utiliza Color y talla");
        }
        // Bloquear las filas afectadas para impedir cambios de stock concurrentes.
        List<Integer> ids = jdbc.queryForList("""
                SELECT pt.id_producto_talla FROM public.producto_tallas pt
                WHERE pt.id_producto = ? AND pt.id_color = ?
                FOR UPDATE
                """, Integer.class, idProducto, idColor);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Este color no tiene variantes en el producto");
        }
        Integer stock = jdbc.queryForObject("""
                SELECT COALESCE(SUM(stock), 0)::integer
                FROM public.producto_tallas
                WHERE id_producto = ? AND id_color = ?
                """, Integer.class, idProducto, idColor);
        if (stock == null || stock != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Color con stock: primero realice un ajuste de inventario auditado");
        }
        Integer fotos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.producto_imagenes
                WHERE id_producto = ? AND id_color = ?
                """, Integer.class, idProducto, idColor);
        if (fotos != null && fotos > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este color tiene fotografias. Asignelas a General u otro color antes de quitarlo");
        }
        Integer referencias = jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM public.detalle_venta dv
                   JOIN public.producto_tallas pt ON pt.id_producto_talla = dv.id_producto_talla
                   WHERE pt.id_producto = ? AND pt.id_color = ?)
                + (SELECT COUNT(*) FROM public.transferencia_stock_reposiciones tr
                   JOIN public.producto_tallas pt ON pt.id_producto_talla = tr.id_producto_talla
                   WHERE pt.id_producto = ? AND pt.id_color = ?)
                + (SELECT COUNT(*) FROM public.yape_inventario_reposiciones yr
                   JOIN public.producto_tallas pt ON pt.id_producto_talla = yr.id_producto_talla
                   WHERE pt.id_producto = ? AND pt.id_color = ?)
                """, Integer.class, idProducto, idColor, idProducto, idColor, idProducto, idColor);
        if (referencias == null || referencias > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El color tiene movimientos historicos; no es seguro eliminar sus variantes");
        }
        // Las restricciones FK de PostgreSQL siguen protegiendo cualquier otra referencia.
        int quitadas = jdbc.update("""
                DELETE FROM public.producto_tallas
                WHERE id_producto = ? AND id_color = ? AND stock = 0
                """, idProducto, idColor);
        if (quitadas != ids.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El inventario cambio durante la operacion; se revirtio todo");
        }
        return new Resultado(quitadas, "Color quitado solo de este producto");
    }
}
