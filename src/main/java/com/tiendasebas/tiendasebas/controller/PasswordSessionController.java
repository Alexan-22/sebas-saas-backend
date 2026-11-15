package com.tiendasebas.tiendasebas.controller;
import com.tiendasebas.tiendasebas.security.*;
import com.tiendasebas.tiendasebas.config.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class PasswordSessionController {
 private final PasswordRecoveryService recovery;private final SesionService sesiones;private final JwtService jwt;private final AuthRateLimit limites;
 public PasswordSessionController(PasswordRecoveryService r,SesionService s,JwtService j,AuthRateLimit limites){recovery=r;sesiones=s;jwt=j;this.limites=limites;}
 public record Forgot(@NotBlank @Email @Size(max=150) String correo){}
 public record Reset(@NotBlank @Size(max=100) String token,@NotBlank @Size(max=100) String passwordNueva){}
 public record Change(@NotBlank @Size(max=100) String passwordActual,@NotBlank @Size(max=100) String passwordNueva){}
 private Long tenant(HttpServletRequest request){return request.getServletPath().startsWith("/api/auth/superadmin/")||request.getServletPath().startsWith("/api/superadmin/")?null:TenantContext.getCurrentTenant();}
 @PostMapping({"/api/auth/password/forgot","/api/auth/superadmin/password/forgot"})
 ResponseEntity<Map<String,String>> forgot(@Valid @RequestBody Forgot body,HttpServletRequest r){limites.comprobar("recovery:"+tenant(r),body.correo().trim().toLowerCase(java.util.Locale.ROOT),5);recovery.solicitar(body.correo(),tenant(r));return ResponseEntity.accepted().body(Map.of("message","Si la cuenta cumple los requisitos, recibiras instrucciones."));}
 @PostMapping({"/api/auth/password/reset","/api/auth/superadmin/password/reset"})
 ResponseEntity<Void> reset(@Valid @RequestBody Reset body,HttpServletRequest r){recovery.restablecer(body.token(),body.passwordNueva(),tenant(r));return ResponseEntity.noContent().build();}
 @PostMapping({"/api/auth/password/change","/api/superadmin/auth/password/change"})
 ResponseEntity<Void> change(@Valid @RequestBody Change body,HttpServletRequest r,Authentication auth){limites.comprobar("password-change:"+tenant(r),auth.getName(),10);recovery.cambiar(auth.getName(),tenant(r),body.passwordActual(),body.passwordNueva());return ResponseEntity.noContent().build();}
 @PostMapping({"/api/auth/logout","/api/superadmin/auth/logout"})
 ResponseEntity<Void> logout(HttpServletRequest r){String token=r.getHeader("Authorization").substring(7).trim();sesiones.revocar(jwt.getClaims(token).getId());return ResponseEntity.noContent().build();}
}
