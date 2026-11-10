package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.ProductoTalla;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoTallaRepository extends JpaRepository<ProductoTalla, Integer> {

    // MÃ©todos base requeridos por ProductoTallaService
    List<ProductoTalla> findByProductoIdProducto(Integer idProducto);
    Optional<ProductoTalla> findByProductoIdProductoAndTallaIdTalla(Integer idProducto, Integer idTalla);

    @Query("""
            SELECT pt
            FROM ProductoTalla pt
            JOIN FETCH pt.producto p
            JOIN FETCH pt.talla t
            WHERE p.tiendaId = :tiendaId
            ORDER BY p.idProducto, t.idTalla
            """)
    List<ProductoTalla> listarInventarioCompletoPorTienda(@Param("tiendaId") Long tiendaId);

    // MÃ©todos Multi-tenant (ya agregados previamente)
    List<ProductoTalla> findByProductoIdProductoAndProductoTiendaId(Integer idProducto, Long tiendaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT pt
            FROM ProductoTalla pt
            JOIN FETCH pt.producto p
            JOIN FETCH pt.talla
            WHERE pt.idProductoTalla = :idProductoTalla
              AND p.tiendaId = :tiendaId
            """)
    Optional<ProductoTalla> buscarPorIdParaCompra(@Param("idProductoTalla") Integer idProductoTalla, @Param("tiendaId") Long tiendaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT pt FROM ProductoTalla pt
            JOIN FETCH pt.producto p
            JOIN FETCH pt.talla
            WHERE pt.idProductoTalla = :id
              AND p.tiendaId = :tiendaId
            """)
    Optional<ProductoTalla> buscarPorIdConBloqueo(@Param("id") Integer id, @Param("tiendaId") Long tiendaId);

    Optional<ProductoTalla> findByProductoIdProductoAndTallaIdTallaAndProductoTiendaId(
            Integer idProducto, Integer idTalla, Long tiendaId);
}
