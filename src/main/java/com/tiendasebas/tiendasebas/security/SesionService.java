package com.tiendasebas.tiendasebas.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
@org.springframework.transaction.annotation.Transactional
public class SesionService {
 private final JdbcTemplate jdbc;
 private final long idleSeconds,absoluteSeconds;
 public SesionService(JdbcTemplate jdbc,@Value("${app.auth.idle-seconds:1800}") long idle,@Value("${app.auth.absolute-seconds:28800}") long absolute){
  if(idle<1||absolute<idle||absolute>86400)throw new IllegalArgumentException("Politica de sesion invalida");
  this.jdbc=jdbc;idleSeconds=idle;absoluteSeconds=absolute;
 }
 public UUID crear(Integer user,Long tienda){
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO auth_sesiones(id,usuario_id,tienda_id,creada_en,ultimo_uso,vence_en,revocada) VALUES (?,?,?,clock_timestamp(),clock_timestamp(),clock_timestamp()+(? * interval '1 second'),false)",id,user,tienda,absoluteSeconds);
  return id;
 }
 public long absoluteMillis(){return absoluteSeconds*1000;}
 public boolean validar(String raw,Integer user,Long tienda){
  UUID id;try{id=UUID.fromString(raw);}catch(RuntimeException ex){return false;}
  // El predicado y la renovacion son atomicos: una sesion vencida nunca revive.
  return jdbc.update("UPDATE auth_sesiones SET ultimo_uso=clock_timestamp() WHERE id=? AND usuario_id=? AND tienda_id IS NOT DISTINCT FROM ? AND revocada=false AND vence_en>clock_timestamp() AND ultimo_uso>clock_timestamp()-(? * interval '1 second')",id,user,tienda,idleSeconds)==1;
 }
 public void revocar(String raw){try{jdbc.update("UPDATE auth_sesiones SET revocada=true WHERE id=?",UUID.fromString(raw));}catch(IllegalArgumentException ignored){}}
 public void revocarTodas(Integer user){jdbc.update("UPDATE auth_sesiones SET revocada=true WHERE usuario_id=?",user);}
}
