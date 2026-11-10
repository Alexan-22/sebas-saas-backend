package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    Optional<Tienda> findBySubdominio(String subdominio);
}