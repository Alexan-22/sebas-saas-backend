package com.tiendasebas.tiendasebas.pagos;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class SandboxPaymentProviderTest {
    @org.junit.jupiter.api.BeforeEach void authenticate() {
        com.tiendasebas.tiendasebas.config.TenantContext.setCurrentTenant(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin", null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    @org.junit.jupiter.api.AfterEach void clear() {
        com.tiendasebas.tiendasebas.config.TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    private SandboxPaymentProvider provider() {
        var env = new org.springframework.mock.env.MockEnvironment().withProperty("payments.sandbox.enabled", "true");
        env.setActiveProfiles("test", "payments-sandbox");
        return new SandboxPaymentProvider(new SandboxPolicy(env));
    }
    private static PaymentProvider.Solicitud solicitud(long tienda, long pedido, String key) {
        return new PaymentProvider.Solicitud(tienda, pedido,
                new BigDecimal("49.90"), "PEN", key);
    }

    @Test
    void noPermiteConsultarPagoDeOtraTienda() {
        var proveedor = provider();
        var pago = proveedor.crear(solicitud(1, 100, "abc"));
        assertThrows(NoSuchElementException.class, () -> proveedor.consultar(2, pago.id()));
        assertThrows(NoSuchElementException.class, () -> proveedor.aprobarSimulacion(2, pago.id()));
    }

    @Test
    void idempotenciaNoCreaDuplicados() {
        var proveedor = provider();
        var primero = proveedor.crear(solicitud(1, 100, "idem-1"));
        var segundo = proveedor.crear(solicitud(1, 100, "idem-1"));
        assertEquals(primero.id(), segundo.id());
        assertThrows(IllegalStateException.class, () -> proveedor.crear(solicitud(1, 101, "idem-1")));
    }

    @Test
    void cuentasIndependientesPorTienda() {
        var proveedor = provider();
        var a = proveedor.crear(solicitud(1, 100, "misma-clave"));
        com.tiendasebas.tiendasebas.config.TenantContext.setCurrentTenant(2L);
        var b = proveedor.crear(solicitud(2, 100, "misma-clave"));
        assertNotEquals(a.id(), b.id());
    }

    @Test
    void confirmacionRepetidaEsIdempotente() {
        var proveedor = provider();
        var pago = proveedor.crear(solicitud(1, 100, "idem-2"));
        assertEquals(PaymentProvider.Estado.PENDIENTE, pago.estado());
        var primero = proveedor.aprobarSimulacion(1, pago.id());
        var segundo = proveedor.aprobarSimulacion(1, pago.id());
        assertEquals(primero, segundo);
        assertEquals(PaymentProvider.Estado.APROBADO, segundo.estado());
    }
}
