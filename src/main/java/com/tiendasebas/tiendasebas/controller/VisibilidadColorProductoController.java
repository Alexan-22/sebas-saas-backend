package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tiendas/admin/visibilidad-colores")
public class VisibilidadColorProductoController {
    private final JdbcTemplate jdbc;
    public VisibilidadColorProductoController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Estado(Long idColor, String nombre, String codigoHex, int stock, boolean oculto) {}
    public record Resultado(Integer idProducto, Long idColor, boolean oculto) {}

    private Long tenant() {
        Long id = TenantContext.getCurrentTenant();
        if (id == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tienda no identificada");
        return id;
    }

    private void validar(Integer producto, Long color, Long tienda) {
        if (producto == null || producto <= 0 || color == null || color <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificadores invalidos");
        }
        Integer existe = jdbc.queryForObject("""
             SELECT COUNT(*) FROM public.productos p
             JOIN public.saas_colores_tienda c ON c.tienda_id = p.tienda_id AND c.id_color = ?
             WHERE p.id_producto = ? AND p.tienda_id = ?
               AND p.tipo_variantes = 'COLOR_Y_TALLA'
               AND EXISTS (SELECT 1 FROM public.producto_tallas pt
                           WHERE pt.id_producto = p.id_producto AND pt.id_color = c.id_color)
             """, Integer.class, color, producto, tienda);
        if (existe == null || existe != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Color no registrado en este producto de la tienda");
        }
    }

    @GetMapping("/producto/{producto}")
    public List<Estado> listar(@PathVariable Integer producto) {
        Long tienda = tenant();
        Integer existe = jdbc.queryForObject("""
            SELECT COUNT(*) FROM public.productos p
            WHERE p.id_producto=? AND p.tienda_id=? AND p.tipo_variantes='COLOR_Y_TALLA'
            """, Integer.class, producto, tienda);
        if (existe == null || existe != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        return jdbc.query("""
           SELECT c.id_color, c.nombre, c.codigo_hex,
                  COALESCE(SUM(pt.stock),0)::integer AS stock,
                  (oc.id_color IS NOT NULL) AS oculto
           FROM public.producto_tallas pt
           JOIN public.saas_colores_tienda c ON c.id_color=pt.id_color AND c.tienda_id=?
           LEFT JOIN public.saas_producto_colores_ocultos oc
             ON oc.tienda_id=? AND oc.id_producto=pt.id_producto AND oc.id_color=pt.id_color
           WHERE pt.id_producto=?
           GROUP BY c.id_color,c.nombre,c.codigo_hex,oc.id_color
           ORDER BY c.nombre
           """, (rs,i) -> new Estado(rs.getLong("id_color"),rs.getString("nombre"),
                rs.getString("codigo_hex"),rs.getInt("stock"),rs.getBoolean("oculto")),
                tienda,tienda,producto);
    }

    @PutMapping("/producto/{producto}/color/{color}/ocultar")
    @Transactional
    public Resultado ocultar(@PathVariable Integer producto, @PathVariable Long color) {
        Long tienda = tenant();
        validar(producto,color,tienda);
        jdbc.update("""
            INSERT INTO public.saas_producto_colores_ocultos (tienda_id,id_producto,id_color)
            VALUES (?,?,?) ON CONFLICT (tienda_id,id_producto,id_color) DO NOTHING
            """, tienda,producto,color);
        return new Resultado(producto,color,true);
    }

    @DeleteMapping("/producto/{producto}/color/{color}/ocultar")
    @Transactional
    public Resultado mostrar(@PathVariable Integer producto, @PathVariable Long color) {
        Long tienda = tenant();
        validar(producto,color,tienda);
        jdbc.update("""
            DELETE FROM public.saas_producto_colores_ocultos
            WHERE tienda_id=? AND id_producto=? AND id_color=?
            """, tienda,producto,color);
        return new Resultado(producto,color,false);
    }
}
