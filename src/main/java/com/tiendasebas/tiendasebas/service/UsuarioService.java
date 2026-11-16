package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.UsuarioAdminResponse;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    // Listar todos los usuarios
    public List<UsuarioAdminResponse> listarTodos() {
        return usuarioRepository.findByTiendaId(tiendaActual())
                .stream()
                .map(this::convertirDto)
                .toList();
    }

    // Buscar usuario por ID
    public UsuarioAdminResponse buscarPorId(Integer idUsuario) {
        return convertirDto(buscarEntidad(idUsuario));
    }

    // Cambiar estado del usuario
    public UsuarioAdminResponse cambiarEstado(Integer idUsuario, Boolean estado) {
        if (estado == null) {
            throw new RuntimeException("El estado es obligatorio");
        }

        Usuario usuario = buscarEntidad(idUsuario);

        if (!estado && Boolean.TRUE.equals(usuario.getEstado())) {
            usuario.revocarSesiones();
        }

        usuario.setEstado(estado);
        Usuario actualizado = usuarioRepository.save(usuario);
        return convertirDto(actualizado);
    }

    // Buscar entidad usuario en la tienda actual
    private Usuario buscarEntidad(Integer idUsuario) {
        return usuarioRepository.findByIdUsuarioAndTiendaId(idUsuario, tiendaActual())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    // Obtener el ID de la tienda actual desde el contexto
    private Long tiendaActual() {
        Long tienda = TenantContext.getCurrentTenant();
        if (tienda == null || tienda <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return tienda;
    }

    // Convertir entidad a DTO
    private UsuarioAdminResponse convertirDto(Usuario usuario) {
        String rol = usuario.getRol() != null ? usuario.getRol().getNombreRol() : "";

        return new UsuarioAdminResponse(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                rol,
                usuario.getEstado()
        );
    }
}
