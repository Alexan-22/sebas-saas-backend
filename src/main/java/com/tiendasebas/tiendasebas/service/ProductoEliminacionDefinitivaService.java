
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductoEliminacionDefinitivaService {

    private final JdbcTemplate jdbc;

    // Tablas auxiliares que se pueden borrar
    // únicamente cuando no hay historial protegido.
    private static final Set<String> AUXILIARES = Set.of(
            "producto_imagenes",
            "producto_tallas",
            "saas_b2b_escalas",
            "saas_producto_colores_ocultos"
    );

    public ProductoEliminacionDefinitivaService(
            JdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void eliminarDefinitivamente(Integer idProducto) {

        Long tiendaId = TenantContext.getCurrentTenant();

        if (tiendaId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No se identificó la tienda"
            );
        }

        if (idProducto == null || idProducto <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identificador de producto inválido"
            );
        }

        // Bloquear la fila durante la operación.
        // Comprobar siempre el tenant.
        List<Integer> productos = jdbc.queryForList(
                """
                SELECT id_producto
                FROM public.productos
                WHERE id_producto = ?
                  AND tienda_id = ?
                FOR UPDATE
                """,
                Integer.class,
                idProducto,
                tiendaId
        );

        if (productos.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto no encontrado en esta tienda"
            );
        }

        // Bloquear variantes asociadas para evitar
        // modificaciones concurrentes durante el borrado.
        jdbc.queryForList(
                """
                SELECT id_producto_talla
                FROM public.producto_tallas
                WHERE id_producto = ?
                FOR UPDATE
                """,
                Integer.class,
                idProducto
        );

        // Revisar todas las claves foráneas reales
        // que apuntan a producto_tallas.
        verificarReferencias(
                "producto_tallas",
                "id_producto_talla",
                """
                SELECT id_producto_talla
                FROM public.producto_tallas
                WHERE id_producto = ?
                """,
                idProducto,
                Set.of()
        );

        // Revisar otras tablas que apuntan
        // directamente a productos.
        verificarReferencias(
                "productos",
                "id_producto",
                """
                SELECT id_producto
                FROM public.productos
                WHERE id_producto = ?
                """,
                idProducto,
                AUXILIARES
        );

        // Borrar relaciones auxiliares.
        jdbc.update(
                """
                DELETE FROM public.producto_imagenes
                WHERE id_producto = ?
                """,
                idProducto
        );

        jdbc.update(
                """
                DELETE FROM public.saas_b2b_escalas
                WHERE id_producto = ?
                """,
                idProducto
        );

        // Primero eliminar tallas, luego producto.
        jdbc.update(
                """
                DELETE FROM public.producto_tallas
                WHERE id_producto = ?
                """,
                idProducto
        );

        // saas_producto_colores_ocultos tiene
        // ON DELETE CASCADE según la revisión
        // realizada en Supabase.

        int eliminados = jdbc.update(
                """
                DELETE FROM public.productos
                WHERE id_producto = ?
                  AND tienda_id = ?
                """,
                idProducto,
                tiendaId
        );

        if (eliminados != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pudo confirmar la eliminación"
            );
        }
    }

    // =====================================================
    // COMPROBAR REFERENCIAS DEL ESQUEMA POSTGRESQL
    // =====================================================

    private void verificarReferencias(
            String tablaObjetivo,
            String columnaObjetivo,
            String subconsulta,
            Integer idProducto,
            Set<String> auxiliaresPermitidos
    ) {

        List<Map<String, Object>> relaciones =
                jdbc.queryForList(
                        """
                        SELECT
                            ns.nspname AS esquema,
                            origen.relname AS tabla,
                            att.attname AS columna,
                            referencia.attname AS columna_referenciada,
                            cardinality(con.conkey) AS columnas_fk,
                            cardinality(con.confkey) AS columnas_referencia
                        FROM pg_constraint con
                        JOIN pg_class origen
                          ON origen.oid = con.conrelid
                        JOIN pg_namespace ns
                          ON ns.oid = origen.relnamespace
                        JOIN pg_class destino
                          ON destino.oid = con.confrelid
                        JOIN pg_namespace ns_destino
                          ON ns_destino.oid = destino.relnamespace
                        LEFT JOIN pg_attribute att
                          ON att.attrelid = con.conrelid
                         AND att.attnum = con.conkey[1]
                        LEFT JOIN pg_attribute referencia
                          ON referencia.attrelid = con.confrelid
                         AND referencia.attnum = con.confkey[1]
                        WHERE con.contype = 'f'
                          AND ns_destino.nspname = 'public'
                          AND destino.relname = ?
                        """,
                        tablaObjetivo
                );

        for (Map<String, Object> relacion : relaciones) {

            String esquema = String.valueOf(
                    relacion.get("esquema")
            );

            String tabla = String.valueOf(
                    relacion.get("tabla")
            );

            String columna = String.valueOf(
                    relacion.get("columna")
            );

            String columnaReferenciada = String.valueOf(
                    relacion.get("columna_referenciada")
            );

            int columnasFk = ((Number)
                    relacion.get("columnas_fk")).intValue();

            int columnasReferencia = ((Number)
                    relacion.get("columnas_referencia")).intValue();

            // No borrar si aparece una relación
            // compuesta que no sabemos procesar.
            if (columnasFk != 1 || columnasReferencia != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Existe una relación compleja que impide eliminar"
                );
            }

            if (!columnaObjetivo.equals(columnaReferenciada)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Existe una referencia no prevista"
                );
            }

            if (!"public".equals(esquema)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Existe una relación fuera del esquema público"
                );
            }

            if (auxiliaresPermitidos.contains(tabla)) {
                continue;
            }

            String sql =
                    "SELECT EXISTS (" +
                            "SELECT 1 FROM " +
                            identificador(esquema) + "." +
                            identificador(tabla) +
                            " WHERE " + identificador(columna) +
                            " IN (" + subconsulta + ")" +
                            ")";

            Boolean existe = jdbc.queryForObject(
                    sql,
                    Boolean.class,
                    idProducto
            );

            if (Boolean.TRUE.equals(existe)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No se puede eliminar el producto: " +
                                "tiene registros relacionados en " +
                                tabla + ". Desactívalo en su lugar."
                );
            }
        }
    }

    // Escapar correctamente los nombres de
    // columnas y tablas obtenidos del catálogo.
    private String identificador(String nombre) {
        return "\"" + nombre.replace("\"", "\"\"") + "\"";
    }
}
