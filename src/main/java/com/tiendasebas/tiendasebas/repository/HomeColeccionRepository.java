package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.HomeColeccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HomeColeccionRepository extends JpaRepository<HomeColeccion, Integer> {

    // Métodos requeridos por HomeService
    List<HomeColeccion> findByEstadoTrueOrderByOrdenAsc();
    List<HomeColeccion> findAllByOrderByOrdenAsc();

    // Métodos Multi-tenant
    List<HomeColeccion> findByEstadoTrueAndTiendaIdOrderByOrdenAsc(Long tiendaId);
    List<HomeColeccion> findByTiendaIdOrderByOrdenAsc(Long tiendaId);
}