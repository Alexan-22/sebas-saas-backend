package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.DetalleVenta;
import com.tiendasebas.tiendasebas.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Integer> {

    List<DetalleVenta> findByVenta(Venta venta);

    List<DetalleVenta> findByVentaOrderByIdDetalleVentaAsc(Venta venta);

    // Reportes de detalles de venta filtrados por tienda
    @Query("""
            SELECT dv
            FROM DetalleVenta dv
            JOIN FETCH dv.venta v
            JOIN FETCH dv.productoTalla pt
            JOIN FETCH pt.producto p
            JOIN FETCH p.categoria c
            JOIN FETCH p.genero g
            JOIN FETCH pt.talla t
            WHERE v.tiendaId = :tiendaId
            ORDER BY dv.idDetalleVenta ASC
            """)
    List<DetalleVenta> listarTodosParaReportesPorTienda(@Param("tiendaId") Long tiendaId);
}