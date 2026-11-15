package com.tiendasebas.tiendasebas.security;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public final class PasswordPolicy {
 private PasswordPolicy(){}
 public static void validar(String value){
  if(value==null||value.length()<12||value.getBytes(StandardCharsets.UTF_8).length>72||value.indexOf(0)>=0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La contrasena debe tener al menos 12 caracteres y como maximo 72 bytes UTF-8.");
 }
 public static boolean compatible(String value){return value!=null&&value.getBytes(StandardCharsets.UTF_8).length<=72&&value.indexOf(0)<0;}
}
