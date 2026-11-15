package com.tiendasebas.tiendasebas.security;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Configuration(proxyBeanMethods=false)
public class RecoveryDeliveryConfig {
 @Bean @ConditionalOnMissingBean(RecoveryDelivery.class)
 RecoveryDelivery recoveryDelivery(){return new RecoveryDelivery(){
  public boolean disponible(){return false;}
  public void enviar(String c,String t,Long tenant){throw new IllegalStateException("Entrega segura no configurada");}
 };}
}
