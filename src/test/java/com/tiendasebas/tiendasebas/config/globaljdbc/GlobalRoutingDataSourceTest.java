package com.tiendasebas.tiendasebas.config.globaljdbc;

import jakarta.servlet.ServletException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalRoutingDataSourceTest {
    private DataSource boutique, auth, ops;
    private Connection boutiqueConnection, authConnection, opsConnection;
    private GlobalRoutingDataSource routing;

    @BeforeEach
    void setup() throws Exception {
        boutique = mock(DataSource.class); auth = mock(DataSource.class); ops = mock(DataSource.class);
        boutiqueConnection = mock(Connection.class); authConnection = mock(Connection.class); opsConnection = mock(Connection.class);
        when(boutique.getConnection()).thenReturn(boutiqueConnection);
        when(auth.getConnection()).thenReturn(authConnection);
        when(ops.getConnection()).thenReturn(opsConnection);
        routing = new GlobalRoutingDataSource(boutique, auth, ops);
    }

    @AfterEach
    void clear() { GlobalRequestScope.clear(); SecurityContextHolder.clearContext(); }

    private static void identity(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "fixture@example.test", null, List.of(new SimpleGrantedAuthority(role))));
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private void noConnectionWasRequested() { verifyNoInteractions(boutique, auth, ops); }

    @Test void operationsWithoutAuthenticationAreDeniedWithoutBorrowingAnyConnection() {
        GlobalRequestScope.set(GlobalRequestScope.Mode.OPERATIONS);
        var error = assertThrows(SQLInvalidAuthorizationSpecException.class, routing::getConnection);
        assertEquals("28000", error.getSQLState());
        noConnectionWasRequested();
    }

    @Test void boutiqueCannotBorrowAnyConnectionInOperationsScope() {
        identity("ROLE_ADMIN"); GlobalRequestScope.set(GlobalRequestScope.Mode.OPERATIONS);
        assertThrows(SQLInvalidAuthorizationSpecException.class, routing::getConnection);
        noConnectionWasRequested();
    }

    @Test void superadminAuthorityWithoutAuthenticatedIdentityIsDenied() {
        identity("ROLE_SUPER_ADMIN");
        SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);
        GlobalRequestScope.set(GlobalRequestScope.Mode.OPERATIONS);
        assertThrows(SQLInvalidAuthorizationSpecException.class, routing::getConnection);
        noConnectionWasRequested();
    }

    @Test void authorizedSuperadminBorrowsOnlyOperationsPool() throws Exception {
        identity("ROLE_SUPER_ADMIN"); GlobalRequestScope.set(GlobalRequestScope.Mode.OPERATIONS);
        assertSame(opsConnection, routing.getConnection());
        verify(ops).getConnection(); verifyNoInteractions(auth, boutique);
    }

    @Test void explicitAuthenticationScopeNeverPromotesEvenForSuperadmin() throws Exception {
        identity("ROLE_SUPER_ADMIN"); GlobalRequestScope.set(GlobalRequestScope.Mode.AUTHENTICATION);
        assertSame(authConnection, routing.getConnection());
        verify(auth).getConnection(); verifyNoInteractions(ops, boutique);
    }

    @Test void publicSuperadminAuthenticationRoutesUseOnlyAuthenticationPool() throws Exception {
        for (String path : List.of("/api/auth/superadmin/login", "/api/auth/superadmin/password/forgot", "/api/auth/superadmin/password/reset")) {
            new GlobalJdbcRequestFilter().doFilter(request("POST", path), new MockHttpServletResponse(), (req,res) -> {
                assertEquals(GlobalRequestScope.Mode.AUTHENTICATION, GlobalRequestScope.get());
                assertFalse(GlobalRequestScope.operationsRequested());
                try { assertSame(authConnection, routing.getConnection()); } catch (SQLException ex) { throw new ServletException(ex); }
            });
            assertNull(GlobalRequestScope.get());
        }
        verify(auth, times(3)).getConnection(); verifyNoInteractions(ops, boutique);
    }

    @Test void operationalRequestAuthenticatesBeforeExplicitPromotionAfterJwt() throws Exception {
        var response = new MockHttpServletResponse();
        new GlobalJdbcRequestFilter().doFilter(request("GET", "/api/superadmin/tiendas"), response, (req,res) -> {
            assertEquals(GlobalRequestScope.Mode.AUTHENTICATION, GlobalRequestScope.get());
            assertTrue(GlobalRequestScope.operationsRequested());
            try { assertSame(authConnection, routing.getConnection()); } catch (SQLException ex) { throw new ServletException(ex); }
            // Simula exclusivamente el resultado del filtro JWT; la integral usa JWT real.
            identity("ROLE_SUPER_ADMIN");
            new GlobalJdbcAuthorizationFilter().doFilter(req, res, (businessRequest,businessResponse) -> {
                assertEquals(GlobalRequestScope.Mode.OPERATIONS, GlobalRequestScope.get());
                try { assertSame(opsConnection, routing.getConnection()); } catch (SQLException ex) { throw new ServletException(ex); }
            });
            assertEquals(GlobalRequestScope.Mode.AUTHENTICATION, GlobalRequestScope.get());
        });
        assertNull(GlobalRequestScope.get()); assertFalse(GlobalRequestScope.operationsRequested());
        verify(auth).getConnection(); verify(ops).getConnection(); verifyNoInteractions(boutique);
    }

    @Test void operationGateWithoutAuthenticationRejectsWithoutJdbc() throws Exception {
        var response = new MockHttpServletResponse();
        new GlobalJdbcRequestFilter().doFilter(request("GET", "/api/superadmin/tiendas"), response,
                (req,res) -> new GlobalJdbcAuthorizationFilter().doFilter(req,res,(a,b) -> fail("No debe ejecutar negocio")));
        assertEquals(401, response.getStatus());
        assertNull(GlobalRequestScope.get()); assertFalse(GlobalRequestScope.operationsRequested());
        noConnectionWasRequested();
    }

    @Test void operationGateRejectsBoutiqueWithoutJdbc() throws Exception {
        identity("ROLE_ADMIN"); var response = new MockHttpServletResponse();
        new GlobalJdbcRequestFilter().doFilter(request("GET", "/api/superadmin/tiendas"), response,
                (req,res) -> new GlobalJdbcAuthorizationFilter().doFilter(req,res,(a,b) -> fail("No debe ejecutar negocio")));
        assertEquals(403, response.getStatus());
        assertNull(GlobalRequestScope.get()); assertFalse(GlobalRequestScope.operationsRequested());
        noConnectionWasRequested();
    }

    @Test void sameWorkerThreadIsCleanAfterBusinessError() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            long first = worker.submit(() -> {
                identity("ROLE_SUPER_ADMIN");
                try {
                    assertThrows(ServletException.class, () -> new GlobalJdbcRequestFilter().doFilter(
                            request("GET", "/api/superadmin/tiendas"),new MockHttpServletResponse(),
                            (req,res) -> new GlobalJdbcAuthorizationFilter().doFilter(req,res,(a,b) -> {
                                try { assertSame(opsConnection,routing.getConnection()); } catch (SQLException ex) { throw new ServletException(ex); }
                                throw new ServletException("Fallo ficticio de negocio");
                            })));
                    assertNull(GlobalRequestScope.get()); assertFalse(GlobalRequestScope.operationsRequested());
                    return Thread.currentThread().threadId();
                } finally { SecurityContextHolder.clearContext(); }
            }).get(20, TimeUnit.SECONDS);
            long reused = worker.submit(() -> {
                assertNull(GlobalRequestScope.get()); assertFalse(GlobalRequestScope.operationsRequested());
                assertSame(boutiqueConnection,routing.getConnection());
                return Thread.currentThread().threadId();
            }).get(20, TimeUnit.SECONDS);
            assertEquals(first,reused);
        }
        verify(ops).getConnection(); verify(boutique).getConnection(); verifyNoInteractions(auth);
    }

    @Test void scopeIsClearedWhenJdbcAuthorizationThrows() {
        assertThrows(ServletException.class, () -> new GlobalJdbcRequestFilter().doFilter(
                request("GET", "/api/superadmin/tiendas"),new MockHttpServletResponse(),(req,res) -> {
                    GlobalRequestScope.set(GlobalRequestScope.Mode.OPERATIONS);
                    try { routing.getConnection(); fail("Debe denegar conexion"); } catch (SQLException ex) { throw new ServletException(ex); }
                }));
        assertNull(GlobalRequestScope.get()); assertFalse(GlobalRequestScope.operationsRequested());
        noConnectionWasRequested();
    }

    @Test void headersAndParametersCannotSelectGlobalScope() throws Exception {
        var request=request("GET", "/api/productos/public/lista");
        request.addHeader("X-Superadmin", "true");request.addHeader("X-Forwarded-Host", "superadmin.example.test");request.addParameter("scope", "SUPER_ADMIN");
        identity("ROLE_SUPER_ADMIN");
        new GlobalJdbcRequestFilter().doFilter(request,new MockHttpServletResponse(),(req,res) -> {
            assertNull(GlobalRequestScope.get());
            try { assertSame(boutiqueConnection,routing.getConnection()); } catch (SQLException ex) { throw new ServletException(ex); }
        });
        verify(boutique).getConnection();verifyNoInteractions(auth,ops);
    }

    @Test void unsupportedRoutesAreDeniedAndClearThreadLocal() throws Exception {
        identity("ROLE_SUPER_ADMIN");GlobalRequestScope.set(GlobalRequestScope.Mode.OPERATIONS);
        var response=new MockHttpServletResponse();
        new GlobalJdbcRequestFilter().doFilter(request("GET","/api/superadmin/reportes"),response,(req,res) -> fail("Ruta no migrada"));
        assertEquals(403,response.getStatus());assertNull(GlobalRequestScope.get());assertFalse(GlobalRequestScope.operationsRequested());
        noConnectionWasRequested();
    }

    @Test void noHttpScopeCannotPromoteEvenWithSuperadmin() throws Exception {
        identity("ROLE_SUPER_ADMIN");assertSame(boutiqueConnection,routing.getConnection());
        verify(boutique).getConnection();verifyNoInteractions(auth,ops);
    }

    @Test void perCallCredentialsAreRejectedWithoutJdbc() {
        assertThrows(SQLException.class, () -> routing.getConnection("otro", "efimero"));
        noConnectionWasRequested();
    }
}
