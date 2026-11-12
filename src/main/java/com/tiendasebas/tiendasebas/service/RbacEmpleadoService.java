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

    @Transactional
    public EmpleadoResponse crear(
            String nombre,
            String apellido,
            String correo,
            String password
    ) {
        Long tiendaId = TenantContext.getCurrentTenant();

        String email = correo.trim().toLowerCase(Locale.ROOT);

        // La tabla usuarios tiene correo UNIQUE global.
        if (usuarios.existsByCorreo(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo ya esta registrado"
            );
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contrasena supera el limite permitido"
            );
        }

        Rol rolEmpleado = roles.findByNombreRol("EMPLEADO")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Primero registre el rol EMPLEADO"
                ));

        Usuario empleado = new Usuario();
        empleado.setNombre(nombre.trim());
        empleado.setApellido(apellido.trim());
        empleado.setCorreo(email);
        empleado.setPasswordHash(encoder.encode(password));
        empleado.setTiendaId(tiendaId);
        empleado.setRol(rolEmpleado);
        empleado.setEstado(true);

        try {
            Usuario guardado = usuarios.saveAndFlush(empleado);

            return new EmpleadoResponse(
                    guardado.getIdUsuario(),
                    guardado.getNombre(),
                    guardado.getApellido(),
                    guardado.getCorreo(),
                    "EMPLEADO",
                    guardado.getEstado()
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pudo registrar el empleado"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<EmpleadoResponse> listar() {
        Long tiendaId = TenantContext.getCurrentTenant();

        return usuarios.findByTiendaId(tiendaId)
                .stream()
                .filter(u -> u.getRol() != null)
                .filter(u -> "EMPLEADO".equals(
                        u.getRol().getNombreRol()))
                .map(u -> new EmpleadoResponse(
                        u.getIdUsuario(),
                        u.getNombre(),
                        u.getApellido(),
                        u.getCorreo(),
                        "EMPLEADO",
                        u.getEstado()
                ))
                .toList();
    }
}