package com.tiendasebas.tiendasebas.security;
/** Transporte confiable: nunca usar logs, respuesta HTTP o URL Host del cliente para entregar tokens. */
public interface RecoveryDelivery {
 boolean disponible();
 void enviar(String correo,String token,Long tienda);
}
