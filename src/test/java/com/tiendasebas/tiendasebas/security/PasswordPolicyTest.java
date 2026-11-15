package com.tiendasebas.tiendasebas.security;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PasswordPolicyTest {
 @Test void limiteBCryptSeMideEnBytesUtf8(){assertDoesNotThrow(()->PasswordPolicy.validar("é".repeat(36)));assertThrows(RuntimeException.class,()->PasswordPolicy.validar("é".repeat(37)));assertDoesNotThrow(()->PasswordPolicy.validar("a".repeat(72)));}
 @Test void nuevasPasswordsNoPermitenLongitudCortaNiNul(){assertThrows(RuntimeException.class,()->PasswordPolicy.validar("short"));assertThrows(RuntimeException.class,()->PasswordPolicy.validar("Ficticia-2026\0"));}
 @Test void passwordAnteriorNoSeRechazaPorNuevaLongitudMinima(){assertTrue(PasswordPolicy.compatible("Old12345"));assertFalse(PasswordPolicy.compatible("a".repeat(73)));}
 @Test void sinTransporteSeguroNoSeFingeRecuperacion(){var d=new RecoveryDeliveryConfig().recoveryDelivery();assertFalse(d.disponible());assertThrows(IllegalStateException.class,()->d.enviar("ficticio@example.test","token-ficticio",1L));}
}
