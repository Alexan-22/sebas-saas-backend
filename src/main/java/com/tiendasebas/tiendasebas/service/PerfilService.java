
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
import com.tiendasebas.tiendasebas.security.PasswordRecoveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PerfilService {

    private final UsuarioRepository usuarios;
    private final PasswordRecoveryService recovery;
    private final com.tiendasebas.tiendasebas.security.AuthRateLimit limites;

    public PerfilService(
            UsuarioRepository usuarios,
            PasswordRecoveryService recovery, com.tiendasebas.tiendasebas.security.AuthRateLimit limites
    ) {
        this.usuarios = usuarios;
        this.recovery = recovery;
        this.limites = limites;
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

    public void cambiarMiContrasena(CambiarContrasenaRequest datos) {
        if (!java.util.Objects.equals(datos.contrasenaNueva(),datos.confirmarContrasena()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Las contrasenas nuevas no coinciden");
        if(java.util.Objects.equals(datos.contrasenaActual(),datos.contrasenaNueva()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La nueva contrasena debe ser diferente");
        Usuario usuario=usuarioActual();
        limites.comprobar("password-change:"+usuario.getTiendaId(),usuario.getCorreo(),10);
        recovery.cambiar(usuario.getCorreo(),usuario.getTiendaId(),datos.contrasenaActual(),datos.contrasenaNueva());
    }
}
