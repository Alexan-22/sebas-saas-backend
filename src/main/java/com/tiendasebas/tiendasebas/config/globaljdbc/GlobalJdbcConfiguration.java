package com.tiendasebas.tiendasebas.config.globaljdbc;

import com.tiendasebas.tiendasebas.config.TenantJpaTransactionManager;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/** Opt-in local: no cambia perfiles actuales ni registra un ámbito global por GUC. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.rls.superadmin-enabled", havingValue = "true")
public class GlobalJdbcConfiguration {
    private static HikariDataSource pool(Environment env, String prefix, String name) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(name);
        ds.setJdbcUrl(env.getRequiredProperty(prefix + ".url"));
        ds.setUsername(env.getRequiredProperty(prefix + ".username"));
        ds.setPassword(env.getRequiredProperty(prefix + ".password"));
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(10000);
        return ds;
    }

    @Bean(name = "boutiqueJdbcPool", destroyMethod = "close")
    HikariDataSource boutiquePool(Environment env) {
        if (!env.getProperty("app.rls.auth-enabled", Boolean.class, false)
                || env.getProperty("spring.jpa.open-in-view", Boolean.class, true)) {
            throw new IllegalStateException("Requiere tenant RLS y open-in-view=false");
        }
        return pool(env, "spring.datasource", "boutique-jdbc");
    }

    @Bean(name = "globalAuthJdbcPool", destroyMethod = "close")
    HikariDataSource authenticationPool(Environment env) {
        return pool(env, "app.global-jdbc.authentication", "global-auth-jdbc");
    }

    @Bean(name = "globalOperationsJdbcPool", destroyMethod = "close")
    HikariDataSource operationsPool(Environment env) {
        return pool(env, "app.global-jdbc.operations", "global-operations-jdbc");
    }

    @Bean(name = "dataSource")
    @Primary
    DataSource dataSource(@Qualifier("boutiqueJdbcPool") DataSource boutique,
            @Qualifier("globalAuthJdbcPool") DataSource auth,
            @Qualifier("globalOperationsJdbcPool") DataSource operations) {
        return new GlobalRoutingDataSource(boutique, auth, operations);
    }

    @Bean
    TenantJpaTransactionManager transactionManager(EntityManagerFactory emf,
            @Qualifier("dataSource") DataSource ds) {
        return new TenantJpaTransactionManager(emf, ds);
    }

    @Bean
    GlobalJdbcAuthorizationFilter globalJdbcAuthorizationFilter() {
        return new GlobalJdbcAuthorizationFilter();
    }

    @Bean
    FilterRegistrationBean<GlobalJdbcAuthorizationFilter> disableGlobalAuthorizationServletFilter(
            GlobalJdbcAuthorizationFilter filter) {
        FilterRegistrationBean<GlobalJdbcAuthorizationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<GlobalJdbcRequestFilter> globalJdbcRequestFilter() {
        FilterRegistrationBean<GlobalJdbcRequestFilter> registration =
                new FilterRegistrationBean<>(new GlobalJdbcRequestFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setAsyncSupported(false);
        return registration;
    }
}
