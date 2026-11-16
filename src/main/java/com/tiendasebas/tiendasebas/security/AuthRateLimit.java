package com.tiendasebas.tiendasebas.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
@Service
public class AuthRateLimit {
 @Value("${app.rls.auth-enabled:false}") private boolean rlsAuthEnabled;
 private final JdbcTemplate jdbc;private final byte[] secret;
 public AuthRateLimit(JdbcTemplate jdbc,@Value("${app.jwt.secret}") String secret){this.jdbc=jdbc;this.secret=secret.getBytes(StandardCharsets.UTF_8);}
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,noRollbackFor=org.springframework.web.server.ResponseStatusException.class)
 public void comprobar(String scope,String identifier,int max){
  String key;
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));key=HexFormat.of().formatHex(mac.doFinal((scope+":"+identifier).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("Limite de autenticacion no disponible");}
  if(rlsAuthEnabled)jdbc.queryForObject("SELECT set_config('app.auth_rate_key',?,true)",String.class,key);
  Integer count=jdbc.queryForObject("INSERT INTO auth_limites(clave,inicio,intentos) VALUES (?,clock_timestamp(),1) ON CONFLICT(clave) DO UPDATE SET intentos=CASE WHEN auth_limites.inicio<clock_timestamp()-interval '10 minutes' THEN 1 ELSE LEAST(auth_limites.intentos+1,1000000) END,inicio=CASE WHEN auth_limites.inicio<clock_timestamp()-interval '10 minutes' THEN clock_timestamp() ELSE auth_limites.inicio END RETURNING intentos",Integer.class,key);
  if(count==null||count>max)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Demasiados intentos. Intenta mas tarde.");
 }
}
