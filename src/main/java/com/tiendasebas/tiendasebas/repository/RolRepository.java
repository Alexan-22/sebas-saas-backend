package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    Optional<Rol> findByNombreRol(String nombreRol);
}