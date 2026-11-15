package com.tiendasebas.tiendasebas.service;
import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.*;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.*;
import com.tiendasebas.tiendasebas.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class AuthService {
 private final UsuarioRepository usuarios;private final RolRepository roles;private final PasswordEncoder passwords;private final JwtService jwt;private final AuthRateLimit limites;
 private final String dummy;private final org.springframework.transaction.support.TransactionTemplate tx;
 public AuthService(UsuarioRepository usuarios,RolRepository roles,PasswordEncoder passwords,JwtService jwt,AuthRateLimit limites,org.springframework.transaction.PlatformTransactionManager tm){this.usuarios=usuarios;this.roles=roles;this.passwords=passwords;this.jwt=jwt;this.limites=limites;this.tx=new org.springframework.transaction.support.TransactionTemplate(tm);dummy=passwords.encode(UUID.randomUUID().toString());}
 private static String correo(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
 public String registrar(RegistroRequest request){
  PasswordPolicy.validar(request.getPassword());Long tenant=TenantContext.getCurrentTenant();
  if(tenant==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  String email=correo(request.getCorreo());limites.comprobar("registro:"+tenant,email,5);
  String hash=passwords.encode(request.getPassword()); // mismo coste para correo existente o nuevo
  String respuesta="Si los datos permiten el registro, podras iniciar sesion con tus credenciales.";
  var rolCliente=roles.findByNombreRol("CLIENTE").orElseThrow(()->new IllegalStateException("Rol de registro no disponible"));
  if(usuarios.existsByCorreoAndTiendaId(email,tenant))return respuesta;
  Usuario u=new Usuario();u.setNombre(request.getNombre().trim());u.setApellido(request.getApellido().trim());u.setCorreo(email);u.setPasswordHash(hash);u.setTiendaId(tenant);u.setEstado(true);
  u.setRol(rolCliente);
  try{usuarios.saveAndFlush(u);}catch(org.springframework.dao.DataIntegrityViolationException ex){if(!esDuplicado(ex))throw ex;}
  return respuesta;
 }
 public LoginResponse login(LoginRequest request){return iniciar(request,false);}
 public LoginResponse loginSuperAdmin(LoginRequest request){return iniciar(request,true);}
 private LoginResponse iniciar(LoginRequest request,boolean global){
  Long tenant=global?null:TenantContext.getCurrentTenant();if(!global&&tenant==null)throw invalida();
  limites.comprobar("login:"+(global?"global":tenant),correo(request.getCorreo()),15);
  return tx.execute(status->autenticar(request,global));
 }
 private LoginResponse autenticar(LoginRequest request,boolean global){
  String email=correo(request.getCorreo());Long tenant=global?null:TenantContext.getCurrentTenant();
  if(!global&&tenant==null)throw invalida();
  Optional<Usuario> found=global?usuarios.bloquearGlobalPorCorreo(email):usuarios.bloquearPorCorreoTienda(email,tenant);
  Usuario u=found.orElse(null);
  String hash=u==null?dummy:u.getPasswordHash();
  boolean compatible=PasswordPolicy.compatible(request.getPassword());
  boolean matches=passwords.matches(compatible?request.getPassword():"invalid-password",hash);
  String role=u==null||u.getRol()==null?"":u.getRol().getNombreRol();
  if(!compatible||!matches||u==null||!Boolean.TRUE.equals(u.getEstado())||!Objects.equals(u.getCorreo(),email)||!Objects.equals(u.getTiendaId(),tenant)||!(global?"SUPER_ADMIN".equals(role):Set.of("ADMIN","EMPLEADO","CLIENTE").contains(role)))throw invalida();
  String token=jwt.generateToken(u.getIdUsuario(),u.getCorreo(),role,u.getTiendaId(),u.getMaterialSesion());
  return new LoginResponse(token,u.getNombre(),u.getCorreo(),role);
 }
 private boolean esDuplicado(Throwable ex){
  for(Throwable causa=ex;causa!=null;causa=causa.getCause())if(causa instanceof java.sql.SQLException sql&&"23505".equals(sql.getSQLState()))return true;
  return false;
 }
 private ResponseStatusException invalida(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Credenciales incorrectas");}
}
