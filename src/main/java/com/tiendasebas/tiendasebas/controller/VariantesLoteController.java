package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Crea combinaciones nuevas en un solo lote. Nunca sobrescribe stock existente. */
@RestController
@RequestMapping("/api/v1/tiendas/admin/variantes")
public class VariantesLoteController {
    private final JdbcTemplate jdbc;

    public VariantesLoteController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Item(Long idColor, Integer idTalla, Integer stock) {}
    public record Solicitud(Integer idProducto, List<Item> combinaciones) {}
    public record Resultado(int creadas, int yaExistentes, List<Integer> idsCreados) {}

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    @PostMapping("/lote")
    @Transactional
    public Resultado crearLote(@RequestBody Solicitud solicitud) {
        Long tienda = TenantContext.getCurrentTenant();
        if (solicitud == null || solicitud.idProducto() == null || solicitud.idProducto() < 1
                || solicitud.combinaciones() == null || solicitud.combinaciones().isEmpty()
                || solicitud.combinaciones().size() > 300) {
            throw error(HttpStatus.BAD_REQUEST, "Indica producto y entre 1 y 300 combinaciones");
        }

        // El bloqueo impide que dos guardados masivos de este producto se crucen.
        List<String> modos = jdbc.query("""
                SELECT tipo_variantes FROM public.productos
                WHERE id_producto = ? AND tienda_id = ? FOR UPDATE
                """, (rs, i) -> rs.getString(1), solicitud.idProducto(), tienda);
        if (modos.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Producto no encontrado en esta tienda");
        String modo = modos.get(0);
        if (modo == null || !Set.of("SIN_VARIANTES", "SOLO_COLOR", "SOLO_TALLA", "COLOR_Y_TALLA").contains(modo)) {
            throw error(HttpStatus.CONFLICT, "Configura primero la modalidad de variantes");
        }
        boolean colorObligatorio = modo.equals("SOLO_COLOR") || modo.equals("COLOR_Y_TALLA");
        boolean tallaObligatoria = modo.equals("SOLO_TALLA") || modo.equals("COLOR_Y_TALLA");

        // Impedir mezclar stock legacy sin color con variantes nuevas con color.
        if (colorObligatorio) {
            Integer legadas = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM public.producto_tallas
                    WHERE id_producto = ? AND id_color IS NULL
                    """, Integer.class, solicitud.idProducto());
            if (legadas != null && legadas > 0) {
                throw error(HttpStatus.CONFLICT, "Hay inventario anterior sin color; requiere conversion controlada");
            }
        }

        Set<String> clavesLote = new HashSet<>();
        List<Integer> ids = new ArrayList<>();
        int omitidas = 0;

        for (Item item : solicitud.combinaciones()) {
            if (item == null || item.stock() == null || item.stock() < 0 || item.stock() > 1_000_000
                    || colorObligatorio != (item.idColor() != null)
                    || tallaObligatoria != (item.idTalla() != null)) {
                throw error(HttpStatus.BAD_REQUEST, "Combinacion invalida para modalidad " + modo);
            }
            String clave = String.valueOf(item.idColor()) + ":" + String.valueOf(item.idTalla());
            if (!clavesLote.add(clave)) {
                throw error(HttpStatus.BAD_REQUEST, "Combinacion duplicada en lote: " + clave);
            }
            if (item.idColor() != null) {
                Integer existe = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM public.saas_colores_tienda
                        WHERE id_color = ? AND tienda_id = ? AND activo = TRUE
                        """, Integer.class, item.idColor(), tienda);
                if (existe == null || existe != 1) {
                    throw error(HttpStatus.BAD_REQUEST, "Color inactivo o ajeno a la tienda");
                }
            }
            if (item.idTalla() != null) {
                Integer existe = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM public.tallas
                        WHERE id_talla = ? AND estado = TRUE
                        """, Integer.class, item.idTalla());
                if (existe == null || existe != 1) {
                    throw error(HttpStatus.BAD_REQUEST, "Talla inexistente o inactiva");
                }
            }

            Integer duplicada = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM public.producto_tallas
                    WHERE id_producto = ?
                      AND id_color IS NOT DISTINCT FROM ?
                      AND id_talla IS NOT DISTINCT FROM ?
                    """, Integer.class, solicitud.idProducto(), item.idColor(), item.idTalla());
            if (duplicada != null && duplicada > 0) {
                omitidas++;
                continue; // Conservar ID y stock sin ninguna modificación.
            }
            Integer id = jdbc.queryForObject("""
                    INSERT INTO public.producto_tallas (id_producto, id_color, id_talla, stock)
                    VALUES (?, ?, ?, ?)
                    RETURNING id_producto_talla
                    """, Integer.class, solicitud.idProducto(), item.idColor(), item.idTalla(), item.stock());
            ids.add(id);
        }
        return new Resultado(ids.size(), omitidas, List.copyOf(ids));
    }
}
