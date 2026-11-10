
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.ActualizarPerfilRequest;
import com.tiendasebas.tiendasebas.dto.CambiarContrasenaRequest;
import com.tiendasebas.tiendasebas.dto.PerfilResponse;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PerfilService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public PerfilService(
            UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    private Usuario usuarioActual() {

        Authentication autenticacion =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Debes iniciar sesión"
            );
        }

        String correo = autenticacion.getName();
        Long tiendaId = TenantContext.getCurrentTenant();

        if (correo == null || correo.isBlank() || tiendaId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Sesión de tienda no válida"
            );
        }

        Usuario usuario = usuarios
                .findByCorreoAndTiendaId(correo, tiendaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario no encontrado en esta tienda"
                ));

        if (!Boolean.TRUE.equals(usuario.getEstado())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "La cuenta está desactivada"
            );
        }

        return usuario;
    }

    private PerfilResponse convertir(Usuario usuario) {
        return new PerfilResponse(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.getRol() != null
                        ? usuario.getRol().getNombreRol()
                        : "",
                usuario.getTiendaId(),
                usuario.getEstado()
        );
    }

    @Transactional(readOnly = true)
    public PerfilResponse obtenerMiPerfil() {
        return convertir(usuarioActual());
    }

    @Transactional
    public PerfilResponse actualizarMiPerfil(
            ActualizarPerfilRequest datos
    ) {
        Usuario usuario = usuarioActual();

        String nombre = datos.nombre().trim();
        String apellido = datos.apellido().trim();

        if (nombre.isBlank() || apellido.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nombre y apellido son obligatorios"
            );
        }

        usuario.setNombre(nombre);
        usuario.setApellido(apellido);

        // No modificar correo, rol, estado ni tiendaId.
        Usuario actualizado = usuarios.save(usuario);

        return convertir(actualizado);
    }

    @Transactional
    public void cambiarMiContrasena(
            CambiarContrasenaRequest datos
    ) {
        Usuario usuario = usuarioActual();

        if (!passwordEncoder.matches(
                datos.contrasenaActual(),
                usuario.getPasswordHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña actual es incorrecta"
            );
        }

        if (!datos.contrasenaNueva()
                .equals(datos.confirmarContrasena())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Las contraseñas nuevas no coinciden"
            );
        }

        if (datos.contrasenaNueva().equals(datos.contrasenaActual())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe ser diferente"
            );
        }

        if (datos.contrasenaNueva().length() < 12
                || datos.contrasenaNueva().length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener entre 12 y 128 caracteres"
            );
        }

        usuario.setPasswordHash(
                passwordEncoder.encode(datos.contrasenaNueva())
        );

        usuarios.save(usuario);
    }
}
