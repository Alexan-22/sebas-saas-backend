package com.tiendasebas.tiendasebas.config.globaljdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tres pools sin SET ROLE: una conexión adquirida no se promociona dentro de una transacción. */
public final class GlobalRoutingDataSource extends AbstractDataSource {
    private final DataSource boutique;
    private final DataSource authentication;
    private final DataSource operations;

    public GlobalRoutingDataSource(DataSource boutique, DataSource authentication, DataSource operations) {
        this.boutique = boutique;
        this.authentication = authentication;
        this.operations = operations;
    }

    private DataSource selected() throws SQLException {
        GlobalRequestScope.Mode mode = GlobalRequestScope.get();
        if (mode == null) return boutique;
        if (mode == GlobalRequestScope.Mode.AUTHENTICATION) return authentication;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authorized = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (!authorized) {
            throw new SQLInvalidAuthorizationSpecException("Operacion global no autorizada", "28000");
        }
        return operations;
    }

    @Override
    public Connection getConnection() throws SQLException { return selected().getConnection(); }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("No se admiten credenciales JDBC por llamada");
    }
}
