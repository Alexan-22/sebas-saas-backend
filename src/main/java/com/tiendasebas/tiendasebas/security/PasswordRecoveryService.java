package com.tiendasebas.tiendasebas.security;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.*;import java.util.*;import java.nio.charset.StandardCharsets;
@Service
public class PasswordRecoveryService {
 private final UsuarioRepository users;private final JdbcTemplate jdbc;private final PasswordEncoder passwords;private final SesionService sesiones;private final RecoveryDelivery delivery;private final JwtService jwt;
 public PasswordRecoveryService(UsuarioRepository u,JdbcTemplate j,PasswordEncoder p,SesionService s,RecoveryDelivery d,JwtService jwt){users=u;jdbc=j;passwords=p;sesiones=s;delivery=d;this.jwt=jwt;}
 private Usuario lock(Integer id){return users.bloquearPorId(id).orElseThrow(this::invalid);}
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solicitud de credenciales invalida o vencida");}
 private String digest(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 @Transactional public void solicitar(String raw,Long tenant){
  // Mismo estado para todas las cuentas cuando no hay entrega segura configurada.
  if(!delivery.disponible())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Recuperacion de contrasena temporalmente no disponible");
  String email=raw.trim().toLowerCase(Locale.ROOT);
  var found=tenant==null?users.bloquearGlobalPorCorreo(email):users.bloquearPorCorreoTienda(email,tenant);
  if(found.isEmpty())return;
  Usuario u=found.get();
  if(!Boolean.TRUE.equals(u.getEstado())||!Objects.equals(u.getTiendaId(),tenant)||(tenant==null&&!"SUPER_ADMIN".equals(u.getRol().getNombreRol())))return;
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  jdbc.update("UPDATE auth_recuperaciones SET consumida_en=clock_timestamp() WHERE usuario_id=? AND consumida_en IS NULL",u.getIdUsuario());
  jdbc.update("INSERT INTO auth_recuperaciones(digest,usuario_id,tienda_id,version_credenciales,vence_en) VALUES (?,?,?,?,clock_timestamp()+interval '15 minutes')",digest(token),u.getIdUsuario(),tenant,jwt.versionCredenciales(u.getMaterialSesion()));
  delivery.enviar(u.getCorreo(),token,tenant);
 }
 @Transactional public void restablecer(String token,String nueva,Long tenant){
  PasswordPolicy.validar(nueva);if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw invalid();
  String hash=digest(token);
  List<Integer> ids=jdbc.query("SELECT usuario_id FROM auth_recuperaciones WHERE digest=? AND tienda_id IS NOT DISTINCT FROM ?",(rs,i)->rs.getInt(1),hash,tenant);
  if(ids.size()!=1)throw invalid();Usuario u=lock(ids.getFirst());
  String version=jdbc.queryForObject("SELECT version_credenciales FROM auth_recuperaciones WHERE digest=?",String.class,hash);
  if(!jwt.credencialesVigentes(version,u.getMaterialSesion()))throw invalid();
  if(!Boolean.TRUE.equals(u.getEstado())||!Objects.equals(u.getTiendaId(),tenant))throw invalid();
  int consumed=jdbc.update("UPDATE auth_recuperaciones SET consumida_en=clock_timestamp() WHERE digest=? AND usuario_id=? AND tienda_id IS NOT DISTINCT FROM ? AND consumida_en IS NULL AND vence_en>clock_timestamp()",hash,u.getIdUsuario(),tenant);
  if(consumed!=1)throw invalid();
  renovar(u,nueva);
 }
 @Transactional public void cambiar(String correo,Long tenant,String anterior,String nueva){
  PasswordPolicy.validar(nueva);
  var found=tenant==null?users.bloquearGlobalPorCorreo(correo):users.bloquearPorCorreoTienda(correo,tenant);
  Usuario u=found.orElseThrow(this::invalid);
  if(!Boolean.TRUE.equals(u.getEstado())||!Objects.equals(u.getTiendaId(),tenant)||!PasswordPolicy.compatible(anterior)||!passwords.matches(anterior,u.getPasswordHash()))throw invalid();
  renovar(u,nueva);
 }
 private void renovar(Usuario u,String nueva){
  u.setPasswordHash(passwords.encode(nueva));u.revocarSesiones();users.saveAndFlush(u);
  sesiones.revocarTodas(u.getIdUsuario());
  jdbc.update("UPDATE auth_recuperaciones SET consumida_en=clock_timestamp() WHERE usuario_id=? AND consumida_en IS NULL",u.getIdUsuario());
 }
}
