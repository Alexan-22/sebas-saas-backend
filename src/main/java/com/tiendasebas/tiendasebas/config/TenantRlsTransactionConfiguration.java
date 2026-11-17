package com.tiendasebas.tiendasebas.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.rls.auth-enabled",
        havingValue = "true"
)
@ConditionalOnProperty(
        name = "app.rls.superadmin-enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class TenantRlsTransactionConfiguration {

    @Bean(name = "transactionManager")
    public TenantJpaTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource) {

        return new TenantJpaTransactionManager(
                entityManagerFactory,
                dataSource
        );
    }
}