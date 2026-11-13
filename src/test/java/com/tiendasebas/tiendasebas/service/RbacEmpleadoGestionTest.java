
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.Rol;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.RolRepository;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RbacEmpleadoGestionTest {

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final RolRepository roles =
            mock(RolRepository.class);

    private final PasswordEncoder encoder =
            mock(PasswordEncoder.class);

    private final RbacEmpleadoService servicio =
            new RbacEmpleadoService(usuarios, roles, encoder);

    private Rol rol(String nombre) {
        Rol rol = new Rol();
        rol.setNombreRol(nombre);
        return rol;
    }

    private Usuario empleado(Long tiendaId) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(10);
        usuario.setTiendaId(tiendaId);
        usuario.setNombre("Administrador");
        usuario.setApellido("Prueba");
        usuario.setCorreo("admin@ejemplo.test");
        usuario.setRol(rol("EMPLEADO"));
        usuario.setEstado(true);
        return usuario;
    }

    @Test
    void crearAdministradorLoVinculaATienda() {
        try (MockedStatic<TenantContext> tenant =
                     mockStatic(TenantContext.class)) {

            tenant.when(TenantContext::getCurrentTenant)
                    .thenReturn(1L);

            when(roles.findByNombreRol("EMPLEADO"))
                    .thenReturn(Optional.of(rol("EMPLEADO")));

            when(encoder.encode(anyString()))
                    .thenReturn("hash-de-prueba");

            when(usuarios.saveAndFlush(any(Usuario.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var resultado = servicio.crear(
                    "Ana",
                    "Prueba",
                    "ANA@EJEMPLO.TEST",
                    "ContrasenaSegura123!"
            );

            assertEquals("ana@ejemplo.test", resultado.correo());
            assertEquals("EMPLEADO", resultado.rol());

            verify(usuarios).saveAndFlush(argThat(u ->
                    Long.valueOf(1L).equals(u.getTiendaId()) &&
                            "hash-de-prueba".equals(u.getPasswordHash()) &&
                            Boolean.TRUE.equals(u.getEstado())
            ));
        }
    }

    @Test
    void noPermiteEditarAdministradorDeOtraTienda() {
        try (MockedStatic<TenantContext> tenant =
                     mockStatic(TenantContext.class)) {

            tenant.when(TenantContext::getCurrentTenant)
                    .thenReturn(1L);

            when(usuarios.findByIdUsuarioAndTiendaId(10, 1L))
                    .thenReturn(Optional.empty());

            ResponseStatusException error = assertThrows(
                    ResponseStatusException.class,
                    () -> servicio.editar(
                            10,
                            "Nombre",
                            "Apellido",
                            "nuevo@ejemplo.test"
                    )
            );

            assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

            verify(usuarios, never()).saveAndFlush(any());
        }
    }

    @Test
    void noPermiteDesactivarPropietarioComoEmpleado() {
        try (MockedStatic<TenantContext> tenant =
                     mockStatic(TenantContext.class)) {

            tenant.when(TenantContext::getCurrentTenant)
                    .thenReturn(1L);

            Usuario propietario = empleado(1L);
            propietario.setRol(rol("ADMIN"));

            when(usuarios.findByIdUsuarioAndTiendaId(10, 1L))
                    .thenReturn(Optional.of(propietario));

            ResponseStatusException error = assertThrows(
                    ResponseStatusException.class,
                    () -> servicio.cambiarEstado(10, false)
            );

            assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

            verify(usuarios, never()).saveAndFlush(any());
        }
    }

    @Test
    void desactivarEmpleadoConservaSuRegistro() {
        try (MockedStatic<TenantContext> tenant =
                     mockStatic(TenantContext.class)) {

            tenant.when(TenantContext::getCurrentTenant)
                    .thenReturn(1L);

            Usuario usuario = empleado(1L);

            when(usuarios.findByIdUsuarioAndTiendaId(10, 1L))
                    .thenReturn(Optional.of(usuario));

            when(usuarios.saveAndFlush(any(Usuario.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var resultado = servicio.cambiarEstado(10, false);

            assertFalse(resultado.activo());
            assertFalse(usuario.getEstado());

            verify(usuarios).saveAndFlush(usuario);
            verify(usuarios, never()).delete(any());
        }
    }

    @Test
    void correoDuplicadoEsRechazado() {
        try (MockedStatic<TenantContext> tenant =
                     mockStatic(TenantContext.class)) {

            tenant.when(TenantContext::getCurrentTenant)
                    .thenReturn(1L);

            Usuario usuario = empleado(1L);

            when(usuarios.findByIdUsuarioAndTiendaId(10, 1L))
                    .thenReturn(Optional.of(usuario));

            when(usuarios.existsByCorreo("ocupado@ejemplo.test"))
                    .thenReturn(true);

            ResponseStatusException error = assertThrows(
                    ResponseStatusException.class,
                    () -> servicio.editar(
                            10,
                            "Administrador",
                            "Prueba",
                            "ocupado@ejemplo.test"
                    )
            );

            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

            verify(usuarios, never()).saveAndFlush(any());
        }
    }
}
