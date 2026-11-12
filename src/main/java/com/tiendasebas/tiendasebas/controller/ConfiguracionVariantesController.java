package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/tiendas/admin/variantes")
public class ConfiguracionVariantesController {

    private final JdbcTemplate jdbc;

    private static final Set<String> TIPOS = Set.of(
        "SIN_VARIANTES",
        "SOLO_COLOR",
        "SOLO_TALLA",
        "COLOR_Y_TALLA"
    );

    private static final Set<String> SISTEMAS = Set.of(
        "SIN_TALLA", "LETRAS", "CINTURA", "CUELLO",
        "TRAJES", "CALZADO", "MUJER_NUMERICA",
        "LENCERIA", "BEBES", "INFANTIL",
        "HOGAR", "PERSONALIZADO"
    );

    public ConfiguracionVariantesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ConfiguracionRequest(
        String tipo,
        String sistemaTallas
    ) {}

    public record ConfiguracionResponse(
        Integer idProducto,
        String tipo,
        String sistemaTallas
    ) {}

    @PutMapping("/producto/{idProducto}/configuracion")
    @Transactional
    public ConfiguracionResponse configurar(
        @PathVariable Integer idProducto,
        @RequestBody ConfiguracionRequest request
    ) {

        Long tiendaId = TenantContext.getCurrentTenant();

        if (request == null ||
            request.tipo() == null ||
            !TIPOS.contains(request.tipo())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Tipo de variantes no valido"
            );
        }

        boolean requiereTalla =
            "SOLO_TALLA".equals(request.tipo()) ||
            "COLOR_Y_TALLA".equals(request.tipo());

        String sistema = request.sistemaTallas();

        if (!requiereTalla) {
            sistema = "SIN_TALLA";
        } else if (
            sistema == null ||
            !SISTEMAS.contains(sistema) ||
            "SIN_TALLA".equals(sistema)
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Seleccione un sistema de tallas valido"
            );
        }

        List<String> productos = jdbc.query("""
            SELECT COALESCE(tipo_variantes, '')
            FROM public.productos
            WHERE id_producto = ?
              AND tienda_id = ?
            FOR UPDATE
            """,
            (rs, fila) -> rs.getString(1),
            idProducto,
            tiendaId
        );

        if (productos.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Producto no encontrado en esta tienda"
            );
        }

        Integer registros = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM public.producto_tallas
            WHERE id_producto = ?
            """,
            Integer.class,
            idProducto
        );

        if (registros != null && registros > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El producto ya tiene inventario. " +
                "Su configuracion requiere migracion controlada."
            );
        }

        jdbc.update("""
            UPDATE public.productos
            SET tipo_variantes = ?,
                sistema_tallas = ?
            WHERE id_producto = ?
              AND tienda_id = ?
            """,
            request.tipo(),
            sistema,
            idProducto,
            tiendaId
        );

        return new ConfiguracionResponse(
            idProducto,
            request.tipo(),
            sistema
        );
    }
}