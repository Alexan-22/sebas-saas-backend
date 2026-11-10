package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    List<Categoria> findByTiendaId(Long tiendaId);

    Optional<Categoria> findByIdCategoriaAndTiendaId(Integer idCategoria, Long tiendaId);
}