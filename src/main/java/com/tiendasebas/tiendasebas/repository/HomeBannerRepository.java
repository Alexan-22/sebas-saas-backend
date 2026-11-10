package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.HomeBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HomeBannerRepository extends JpaRepository<HomeBanner, Integer> {

    // Métodos requeridos por HomeService
    List<HomeBanner> findByEstadoTrueOrderByOrdenAsc();
    Optional<HomeBanner> findFirstByEstadoTrueOrderByOrdenAsc();
    List<HomeBanner> findAllByOrderByOrdenAsc();

    // Métodos Multi-tenant
    List<HomeBanner> findByEstadoTrueAndTiendaIdOrderByOrdenAsc(Long tiendaId);
    List<HomeBanner> findByTiendaIdOrderByOrdenAsc(Long tiendaId);
}