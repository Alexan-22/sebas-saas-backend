package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.ProductoImagen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, Integer> {

    List<ProductoImagen> findByProductoIdProductoOrderByOrdenAsc(Integer idProducto);

    long countByProductoIdProductoAndProductoTiendaId(Integer idProducto, Long tiendaId);

    List<ProductoImagen> findByProductoIdProductoAndProductoTiendaIdOrderByOrdenAsc(
            Integer idProducto,
            Long tiendaId
    );
}
