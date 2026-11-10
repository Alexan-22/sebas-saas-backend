package com.tiendasebas.tiendasebas.pagos;
import com.tiendasebas.tiendasebas.config.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
class SandboxPolicyTest {
    @BeforeEach void setup() { TenantContext.setCurrentTenant(1L); role("ADMIN"); }
    @AfterEach void cleanup() { TenantContext.clear(); SecurityContextHolder.clearContext(); }
    void role(String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role)))); }
    SandboxPolicy policy(String enabled, String... profiles) { var env = new MockEnvironment(); if (enabled != null) env.setProperty("payments.sandbox.enabled", enabled); env.setActiveProfiles(profiles); return new SandboxPolicy(env); }
    @Test void disabledByDefault() { assertThrows(AccessDeniedException.class, () -> policy(null,"test","payments-sandbox").requireAuthorized()); }
    @Test void profileAloneIsInsufficient() { assertThrows(AccessDeniedException.class, () -> policy("true","payments-sandbox").requireAuthorized()); }
    @Test void productionCannotEnableSimulation() { for (String p : java.util.List.of("prod","production","PROD","staging")) assertThrows(AccessDeniedException.class, () -> policy("true","test","payments-sandbox",p).requireAuthorized()); }
    @Test void ambiguousEnvironmentsRejected() { assertThrows(AccessDeniedException.class, () -> policy("true","test","local","payments-sandbox").requireAuthorized()); }
    @Test void authorizedLocalAdminAccepted() { assertDoesNotThrow(() -> policy("true","local","payments-sandbox").requireAuthorized()); }
    @Test void publicClientRejected() { role("CLIENTE"); assertThrows(AccessDeniedException.class, () -> policy("true","test","payments-sandbox").requireAuthorized()); }
    @Test void globalAdminCannotSimulateCommercialPayments() { role("SUPER_ADMIN"); assertThrows(AccessDeniedException.class, () -> policy("true","test","payments-sandbox").requireAuthorized()); }
    @Test void anonymousRejected() { SecurityContextHolder.clearContext(); assertThrows(AccessDeniedException.class, () -> policy("true","test","payments-sandbox").requireAuthorized()); }
    @Test void missingTenantRejected() { TenantContext.clear(); var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> policy("true","test","payments-sandbox").requireAuthorized()); assertEquals(401,ex.getStatusCode().value()); }
}
