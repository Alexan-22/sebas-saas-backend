package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Integer> {

    List<Venta> findByTiendaId(Long tiendaId);

    List<Venta> findByUsuarioAndTiendaId(Usuario usuario, Long tiendaId);

    List<Venta> findByUsuarioCorreoAndTiendaIdOrderByFechaVentaDesc(
            String correo,
            Long tiendaId
    );

    Optional<Venta> findByIdVentaAndUsuarioCorreoAndTiendaId(
            Integer idVenta,
            String correo,
            Long tiendaId
    );

    Optional<Venta> findByIdVentaAndTiendaId(Integer idVenta, Long tiendaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venta v WHERE v.idVenta = :idVenta AND v.tiendaId = :tiendaId")
    Optional<Venta> buscarPorIdYTiendaConBloqueo(
        @Param("idVenta") Integer idVenta, @Param("tiendaId") Long tiendaId);
}