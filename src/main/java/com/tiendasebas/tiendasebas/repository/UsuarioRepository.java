package com.tiendasebas.tiendasebas.repository;

import com.tiendasebas.tiendasebas.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.idUsuario=:id")
    Optional<Usuario> bloquearPorId(@org.springframework.data.repository.query.Param("id") Integer id);


    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.correo=:correo and u.tiendaId=:tienda")
    Optional<Usuario> bloquearPorCorreoTienda(@org.springframework.data.repository.query.Param("correo") String correo,@org.springframework.data.repository.query.Param("tienda") Long tienda);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.correo=:correo and u.tiendaId is null")
    Optional<Usuario> bloquearGlobalPorCorreo(@org.springframework.data.repository.query.Param("correo") String correo);

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