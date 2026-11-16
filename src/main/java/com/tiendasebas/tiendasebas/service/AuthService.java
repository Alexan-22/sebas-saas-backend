package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.LoginRequest;
import com.tiendasebas.tiendasebas.dto.LoginResponse;
import com.tiendasebas.tiendasebas.dto.RegistroRequest;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.RolRepository;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;
import com.tiendasebas.tiendasebas.security.AuthRateLimit;
import com.tiendasebas.tiendasebas.security.JwtService;
import com.tiendasebas.tiendasebas.security.PasswordPolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

 private static final String MENSAJE_REGISTRO = "Si los datos permiten el registro, podras iniciar sesion con tus credenciales.";
 private static final Set<String> ROLES_TIENDA = Set.of("ADMIN", "EMPLEADO", "CLIENTE");

 private final UsuarioRepository usuarios;
 private final RolRepository roles;
 private final PasswordEncoder passwords;
 private final JwtService jwt;
 private final AuthRateLimit limites;
 private final String dummy;
 private final TransactionTemplate tx;

 public AuthService(
         UsuarioRepository usuarios,
         RolRepository roles,
         PasswordEncoder passwords,
         JwtService jwt,
         AuthRateLimit limites,
         PlatformTransactionManager tm) {
  this.usuarios = usuarios;
  this.roles = roles;
  this.passwords = passwords;
  this.jwt = jwt;
  this.limites = limites;
  this.tx = new TransactionTemplate(tm);
  this.dummy = passwords.encode(UUID.randomUUID().toString());
 }

 public String registrar(RegistroRequest request) {
  PasswordPolicy.validar(request.getPassword());

  Long tenant = TenantContext.getCurrentTenant();
  if (tenant == null) {
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  String email = normalizarCorreo(request.getCorreo());
  limites.comprobar("registro:" + tenant, email, 5);

  // Mismo coste computacional para correo existente o nuevo (evita enumeración por tiempo)
  String hash = passwords.encode(request.getPassword());

  var rolCliente = roles.findByNombreRol("CLIENTE")
          .orElseThrow(() -> new IllegalStateException("Rol de registro no disponible"));

  if (usuarios.existsByCorreoAndTiendaId(email, tenant)) {
   return MENSAJE_REGISTRO;
  }

  Usuario usuario = new Usuario();
  usuario.setNombre(request.getNombre().trim());
  usuario.setApellido(request.getApellido().trim());
  usuario.setCorreo(email);
  usuario.setPasswordHash(hash);
  usuario.setTiendaId(tenant);
  usuario.setEstado(true);
  usuario.setRol(rolCliente);

  try {
   usuarios.saveAndFlush(usuario);
  } catch (DataIntegrityViolationException ex) {
   if (!esDuplicado(ex)) {
    throw ex;
   }
  }

  return MENSAJE_REGISTRO;
 }

 public LoginResponse login(LoginRequest request) {
  return iniciar(request, false);
 }

 public LoginResponse loginSuperAdmin(LoginRequest request) {
  return iniciar(request, true);
 }

 private LoginResponse iniciar(LoginRequest request, boolean global) {
  Long tenant = global ? null : TenantContext.getCurrentTenant();
  if (!global && tenant == null) {
   throw credencialesInvalidas();
  }

  String contextoLimite = "login:" + (global ? "global" : tenant);
  limites.comprobar(contextoLimite, normalizarCorreo(request.getCorreo()), 15);

  return tx.execute(status -> autenticar(request, global));
 }

 private LoginResponse autenticar(LoginRequest request, boolean global) {
  String email = normalizarCorreo(request.getCorreo());
  Long tenant = global ? null : TenantContext.getCurrentTenant();

  if (!global && tenant == null) {
   throw credencialesInvalidas();
  }

  Optional<Usuario> encontrado = global
          ? usuarios.bloquearGlobalPorCorreo(email)
          : usuarios.bloquearPorCorreoTienda(email, tenant);

  Usuario u = encontrado.orElse(null);
  String hash = (u == null) ? dummy : u.getPasswordHash();

  boolean compatible = PasswordPolicy.compatible(request.getPassword());
  String passwordEvaluar = compatible ? request.getPassword() : "invalid-password";
  boolean matches = passwords.matches(passwordEvaluar, hash);

  String role = (u == null || u.getRol() == null) ? "" : u.getRol().getNombreRol();
  boolean rolValido = global
          ? "SUPER_ADMIN".equals(role)
          : ROLES_TIENDA.contains(role);

  if (!compatible
          || !matches
          || u == null
          || !Boolean.TRUE.equals(u.getEstado())
          || !Objects.equals(u.getCorreo(), email)
          || !Objects.equals(u.getTiendaId(), tenant)
          || !rolValido) {
   throw credencialesInvalidas();
  }

  String token = jwt.generateToken(
          u.getIdUsuario(),
          u.getCorreo(),
          role,
          u.getTiendaId(),
          u.getMaterialSesion()
  );

  return new LoginResponse(token, u.getNombre(), u.getCorreo(), role);
 }

 private static String normalizarCorreo(String value) {
  return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
 }

 private boolean esDuplicado(Throwable ex) {
  for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
   if (causa instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
    return true;
   }
  }
  return false;
 }

 private ResponseStatusException credencialesInvalidas() {
  return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
 }
}
