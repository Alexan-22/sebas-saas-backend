package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findByTiendaId(Long tiendaId);

    Optional<Producto> findByIdProductoAndTiendaId(Integer idProducto, Long tiendaId);

    // Productos activos por tienda
    List<Producto> findByEstadoTrueAndTiendaId(Long tiendaId);

    // Por género
    List<Producto> findByEstadoTrueAndGeneroIdGeneroAndTiendaId(
            Integer idGenero,
            Long tiendaId
    );

    // Por categoría
    List<Producto> findByEstadoTrueAndCategoriaIdCategoriaAndTiendaId(
            Integer idCategoria,
            Long tiendaId
    );

    // Género + categoría
    List<Producto> findByEstadoTrueAndGeneroIdGeneroAndCategoriaIdCategoriaAndTiendaId(
            Integer idGenero,
            Integer idCategoria,
            Long tiendaId
    );

    // Buscador
    List<Producto> findByEstadoTrueAndNombreContainingIgnoreCaseAndTiendaId(
            String nombre,
            Long tiendaId
    );

    // Productos destacados
    List<Producto> findByEstadoTrueAndDestacadoTrueAndTiendaId(Long tiendaId);

    // Productos nuevos
    List<Producto> findByEstadoTrueAndNuevoTrueAndTiendaId(Long tiendaId);

    // Productos con oferta
    List<Producto> findByEstadoTrueAndPrecioOfertaIsNotNullAndTiendaId(Long tiendaId);
}