package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.LoginRequest;
import com.tiendasebas.tiendasebas.dto.LoginResponse;
import com.tiendasebas.tiendasebas.dto.RegistroRequest;
import com.tiendasebas.tiendasebas.entity.Rol;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.RolRepository;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;
import com.tiendasebas.tiendasebas.security.JwtService;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // =====================================================
    // REGISTRO NORMAL DE CLIENTES
    // =====================================================

    public String registrar(RegistroRequest request) {

        String nombre = request.getNombre().trim();
        String apellido = request.getApellido().trim();
        String correo = request.getCorreo()
                .trim()
                .toLowerCase(Locale.ROOT);

        Long tiendaId = TenantContext.getCurrentTenant();

        if (usuarioRepository.existsByCorreoAndTiendaId(
                correo, tiendaId
        )) {
            throw new RuntimeException(
                    "El correo ya está registrado"
            );
        }

        Rol rolCliente = rolRepository.findByNombreRol("CLIENTE")
                .orElseThrow(() ->
                        new RuntimeException(
                                "No existe el rol CLIENTE"
                        )
                );

        Usuario usuario = new Usuario();

        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setCorreo(correo);

        usuario.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        usuario.setRol(rolCliente);
        usuario.setTiendaId(tiendaId);
        usuario.setEstado(true);

        usuarioRepository.save(usuario);

        return "Usuario registrado correctamente";
    }

    // =====================================================
    // LOGIN NORMAL DE CADA TIENDA
    // =====================================================

    public LoginResponse login(LoginRequest request) {

        String correo = request.getCorreo()
                .trim()
                .toLowerCase(Locale.ROOT);

        Usuario usuario = usuarioRepository
                .findByCorreoAndTiendaId(
                        correo,
                        TenantContext.getCurrentTenant()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Correo o contraseña incorrectos"
                        )
                );

        if (!Boolean.TRUE.equals(usuario.getEstado())) {
            throw new RuntimeException(
                    "El usuario está deshabilitado"
            );
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                usuario.getPasswordHash()
        )) {
            throw new RuntimeException(
                    "Correo o contraseña incorrectos"
            );
        }

        String rol = usuario.getRol().getNombreRol();

        String token = jwtService.generateToken(
                usuario.getIdUsuario(),
                usuario.getCorreo(),
                rol,
                usuario.getTiendaId()
        );

        return new LoginResponse(
                token,
                usuario.getNombre(),
                usuario.getCorreo(),
                rol
        );
    }

    // =====================================================
    // LOGIN EXCLUSIVO PARA SUPERADMINISTRADOR
    // =====================================================

    public LoginResponse loginSuperAdmin(LoginRequest request) {

        String correo = request.getCorreo() == null
                ? ""
                : request.getCorreo()
                .trim()
                .toLowerCase(Locale.ROOT);

        String password = request.getPassword();

        if (correo.isBlank()
                || password == null
                || password.isBlank()) {
            throw credencialesInvalidas();
        }

        // Buscar únicamente usuarios globales.
        Usuario usuario = usuarioRepository
                .findByCorreoAndTiendaIdIsNull(correo)
                .orElseThrow(this::credencialesInvalidas);

        // Verificar que la cuenta esté habilitada.
        if (!Boolean.TRUE.equals(usuario.getEstado())) {
            throw credencialesInvalidas();
        }

        // Solo SUPER_ADMIN puede usar este acceso.
        if (usuario.getRol() == null
                || !"SUPER_ADMIN".equals(
                usuario.getRol().getNombreRol()
        )) {
            throw credencialesInvalidas();
        }

        // Comprobar contraseña mediante BCrypt.
        if (!passwordEncoder.matches(
                password,
                usuario.getPasswordHash()
        )) {
            throw credencialesInvalidas();
        }

        // El usuario global no pertenece a una tienda.
        // Hay que verificar que JwtService admita tenantId null.
        String token = jwtService.generateToken(
                usuario.getIdUsuario(),
                usuario.getCorreo(),
                "SUPER_ADMIN",
                null
        );

        return new LoginResponse(
                token,
                usuario.getNombre(),
                usuario.getCorreo(),
                "SUPER_ADMIN"
        );
    }

    private ResponseStatusException credencialesInvalidas() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Credenciales incorrectas"
        );
    }
}