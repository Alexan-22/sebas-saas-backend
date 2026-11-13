
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.Rol;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.RolRepository;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class RbacEmpleadoService {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;

    public RbacEmpleadoService(
            UsuarioRepository usuarios,
            RolRepository roles,
            PasswordEncoder encoder
    ) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
    }

    public record EmpleadoResponse(
            Integer id,
            String nombre,
            String apellido,
            String correo,
            String rol,
            Boolean activo
    ) {}

    private Long tiendaActual() {
        Long tiendaId = TenantContext.getCurrentTenant();

        if (tiendaId == null || tiendaId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tienda no identificada"
            );
        }

        return tiendaId;
    }

    private String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El correo es obligatorio"
            );
        }

        String email = correo.trim().toLowerCase(Locale.ROOT);

        if (email.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Correo demasiado largo"
            );
        }

        return email;
    }

    private void validarPassword(String password) {
        if (password == null ||
                password.length() < 10 ||
                password.length() > 72 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Contrasena invalida"
            );
        }
    }

    private Usuario obtenerEmpleado(Integer usuarioId) {
        if (usuarioId == null || usuarioId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identificador invalido"
            );
        }

        Usuario usuario = usuarios
                .findByIdUsuarioAndTiendaId(
                        usuarioId,
                        tiendaActual()
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Administrador no encontrado"
                        )
                );

        if (usuario.getRol() == null ||
                !"EMPLEADO".equals(
                        usuario.getRol().getNombreRol()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Administrador no encontrado"
            );
        }

        return usuario;
    }

    private EmpleadoResponse convertir(Usuario usuario) {
        return new EmpleadoResponse(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                "EMPLEADO",
                usuario.getEstado()
        );
    }

    @Transactional
    public EmpleadoResponse crear(
            String nombre,
            String apellido,
            String correo,
            String password
    ) {
        Long tiendaId = tiendaActual();
        String email = normalizarCorreo(correo);

        validarPassword(password);

        if (usuarios.existsByCorreo(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo ya esta registrado"
            );
        }

        Rol rolEmpleado = roles.findByNombreRol("EMPLEADO")
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "No existe el rol EMPLEADO"
                        )
                );

        Usuario empleado = new Usuario();
        empleado.setNombre(nombre.trim());
        empleado.setApellido(apellido.trim());
        empleado.setCorreo(email);
        empleado.setPasswordHash(encoder.encode(password));
        empleado.setTiendaId(tiendaId);
        empleado.setRol(rolEmpleado);
        empleado.setEstado(true);

        try {
            return convertir(usuarios.saveAndFlush(empleado));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pudo registrar el administrador"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<EmpleadoResponse> listar() {
        return usuarios.findByTiendaId(tiendaActual())
                .stream()
                .filter(usuario ->
                        usuario.getRol() != null &&
                                "EMPLEADO".equals(
                                        usuario.getRol().getNombreRol()
                                )
                )
                .map(this::convertir)
                .toList();
    }

    @Transactional
    public EmpleadoResponse editar(
            Integer usuarioId,
            String nombre,
            String apellido,
            String correo
    ) {
        Usuario empleado = obtenerEmpleado(usuarioId);
        String email = normalizarCorreo(correo);

        if (!empleado.getCorreo().equalsIgnoreCase(email) &&
                usuarios.existsByCorreo(email)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo ya esta registrado"
            );
        }

        empleado.setNombre(nombre.trim());
        empleado.setApellido(apellido.trim());
        empleado.setCorreo(email);

        return convertir(usuarios.saveAndFlush(empleado));
    }

    @Transactional
    public EmpleadoResponse cambiarEstado(
            Integer usuarioId,
            Boolean activo
    ) {
        if (activo == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Estado obligatorio"
            );
        }

        Usuario empleado = obtenerEmpleado(usuarioId);

        empleado.setEstado(activo);

        return convertir(usuarios.saveAndFlush(empleado));
    }
}
