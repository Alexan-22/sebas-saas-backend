package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    // Método requerido por JwtAuthenticationFilter y AuthService
    Optional<Usuario> findByCorreo(String correo);

    // Método requerido por AuthService
    boolean existsByCorreo(String correo);

    // Métodos Multi-tenant
    Optional<Usuario> findByCorreoAndTiendaId(String correo, Long tiendaId);
    boolean existsByCorreoAndTiendaId(String correo, Long tiendaId);
    List<Usuario> findByTiendaId(Long tiendaId);
    Optional<Usuario> findByIdUsuarioAndTiendaId(Integer idUsuario, Long tiendaId);
    Optional<Usuario> findByCorreoAndTiendaIdIsNull(String correo);
}