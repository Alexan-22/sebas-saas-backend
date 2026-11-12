package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tiendas/variantes")
public class VariantePublicaController {

    private final JdbcTemplate jdbc;

    public VariantePublicaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TallaPublica(Integer idTalla, String nombreTalla, Boolean estado) {}

    public record VariantePublica(
            Integer idProductoTalla,
            Integer idProducto,
            TallaPublica talla,
            Long idColor,
            String colorNombre,
            String colorHex,
            Integer stock
    ) {}

    @GetMapping("/producto/{idProducto}")
    public List<VariantePublica> listar(@PathVariable Integer idProducto) {
        Long tiendaId = TenantContext.getCurrentTenant();
        if (idProducto == null || idProducto <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        }

        Integer existe = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.productos
                WHERE id_producto = ? AND tienda_id = ? AND estado = TRUE
                """, Integer.class, idProducto, tiendaId);
        if (existe == null || existe == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no disponible");
        }

        return jdbc.query("""
                SELECT pt.id_producto_talla, pt.id_producto, pt.id_talla,
                       t.nombre_talla, t.estado AS talla_activa,
                       pt.id_color, c.nombre AS color_nombre,
                       c.codigo_hex AS color_hex, pt.stock
                FROM public.producto_tallas pt
                JOIN public.productos p ON p.id_producto = pt.id_producto
                LEFT JOIN public.tallas t ON t.id_talla = pt.id_talla
                LEFT JOIN public.saas_colores_tienda c
                  ON c.id_color = pt.id_color AND c.tienda_id = p.tienda_id
                WHERE pt.id_producto = ? AND p.tienda_id = ? AND p.estado = TRUE
                  AND NOT EXISTS (
                    SELECT 1 FROM public.saas_producto_colores_ocultos oculto
                    WHERE oculto.tienda_id = p.tienda_id
                      AND oculto.id_producto = pt.id_producto
                      AND oculto.id_color = pt.id_color
                  )
                ORDER BY c.nombre NULLS FIRST, t.nombre_talla NULLS FIRST, pt.id_producto_talla
                """, (rs, row) -> {
                    Integer idTalla = rs.getObject("id_talla", Integer.class);
                    return new VariantePublica(
                            rs.getInt("id_producto_talla"),
                            rs.getInt("id_producto"),
                            idTalla == null ? null : new TallaPublica(
                                    idTalla, rs.getString("nombre_talla"),
                                    rs.getBoolean("talla_activa")),
                            rs.getObject("id_color", Long.class),
                            rs.getString("color_nombre"),
                            rs.getString("color_hex"),
                            rs.getInt("stock")
                    );
                }, idProducto, tiendaId);
    }
}
