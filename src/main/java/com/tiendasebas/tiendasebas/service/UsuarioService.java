package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.dto.UsuarioAdminResponse;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    // =========================================================
    // LISTAR TODOS LOS USUARIOS
    // =========================================================
    public List<UsuarioAdminResponse> listarTodos() {
        return usuarioRepository.findByTiendaId(tiendaActual()).stream().map(this::convertirDto).toList();
    }

    // =========================================================
    // BUSCAR USUARIO POR ID
    // =========================================================
    public UsuarioAdminResponse buscarPorId(Integer idUsuario) {
        Usuario usuario = buscarEntidad(idUsuario);
        return convertirDto(usuario);
    }

    // =========================================================
    // CAMBIAR ESTADO
    // =========================================================
    public UsuarioAdminResponse cambiarEstado(Integer idUsuario, Boolean estado) {
        if (estado == null) {
            throw new RuntimeException("El estado es obligatorio");
        }

        Usuario usuario = buscarEntidad(idUsuario);
        if (!estado && Boolean.TRUE.equals(usuario.getEstado())
                && usuario.getRol() != null && "EMPLEADO".equals(usuario.getRol().getNombreRol())) {
            usuario.revocarSesiones();
        }
        usuario.setEstado(estado);

        Usuario actualizado = usuarioRepository.save(usuario);
        return convertirDto(actualizado);
    }

    // =========================================================
    // BUSCAR ENTIDAD
    // =========================================================
    private Usuario buscarEntidad(Integer idUsuario) {
        return usuarioRepository.findByIdUsuarioAndTiendaId(idUsuario, tiendaActual())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private Long tiendaActual() {
        Long tienda = com.tiendasebas.tiendasebas.config.TenantContext.getCurrentTenant();
        if (tienda == null || tienda <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return tienda;
    }

    // =========================================================
    // CONVERTIR ENTIDAD A DTO
    // =========================================================
    private UsuarioAdminResponse convertirDto(Usuario usuario) {
        String rol = "";

        if (usuario.getRol() != null) {
            rol = usuario.getRol().getNombreRol();
        }

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
