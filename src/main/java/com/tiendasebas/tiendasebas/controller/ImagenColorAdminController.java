package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/tiendas/admin/imagenes-color")
public class ImagenColorAdminController {

    private final JdbcTemplate jdbc;

    public ImagenColorAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AsignacionColor(Long idColor) {}

    public record Resultado(
            Integer idProductoImagen,
            Long idColor,
            String mensaje
    ) {}

    @PutMapping("/{idImagen}")
    @Transactional
    public Resultado asignar(
            @PathVariable Integer idImagen,
            @RequestBody AsignacionColor solicitud
    ) {
        Long tiendaId = TenantContext.getCurrentTenant();

        if (idImagen == null || idImagen <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Imagen invalida"
            );
        }

        if (solicitud == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solicitud obligatoria"
            );
        }

        Long idColor = solicitud.idColor();

        if (idColor != null && idColor <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Color invalido"
            );
        }

        // Solo productos COLOR_Y_TALLA.
        // No permite asociar colores de otra tienda.
        // Un color NULL significa imagen general.
        int modificadas = jdbc.update("""
            UPDATE public.producto_imagenes pi
            SET id_color = ?
            FROM public.productos p
            WHERE pi.id_producto_imagen = ?
              AND pi.id_producto = p.id_producto
              AND p.tienda_id = ?
              AND p.tipo_variantes = 'COLOR_Y_TALLA'
              AND (
                  ? IS NULL
                  OR EXISTS (
                      SELECT 1
                      FROM public.saas_colores_tienda c
                      WHERE c.id_color = ?
                        AND c.tienda_id = p.tienda_id
                        AND c.activo = TRUE
                  )
              )
            """,
            idColor,
            idImagen,
            tiendaId,
            idColor,
            idColor
        );

        if (modificadas != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pudo asignar el color. Verifique la imagen, el producto y el color de esta tienda."
            );
        }

        return new Resultado(
                idImagen,
                idColor,
                "Color de fotografia actualizado"
        );
    }
}