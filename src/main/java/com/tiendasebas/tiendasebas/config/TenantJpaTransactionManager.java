package com.tiendasebas.tiendasebas.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;

public class TenantJpaTransactionManager extends JpaTransactionManager {

    public TenantJpaTransactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource) {

        super(entityManagerFactory);
        setDataSource(dataSource);
    }

    @Override
    protected void doBegin(
            Object transaction,
            TransactionDefinition definition) {

        super.doBegin(transaction, definition);

        Long tenantId = null;

        try {
            tenantId = TenantContext.getCurrentTenant();
        } catch (ResponseStatusException ex) {
            // Las operaciones globales no tienen tenant.
        }

        EntityManagerFactory factory = getEntityManagerFactory();

        if (factory == null) {
            throw new IllegalStateException(
                    "EntityManagerFactory no disponible"
            );
        }

        EntityManager entityManager =
                EntityManagerFactoryUtils
                        .getTransactionalEntityManager(factory);

        if (entityManager == null) {
            throw new IllegalStateException(
                    "No existe EntityManager transaccional"
            );
        }

        String tenant = tenantId == null
                ? ""
                : tenantId.toString();

        entityManager.createNativeQuery(
                "SELECT set_config('app.tenant_id', :tenant, true)"
        )
        .setParameter("tenant", tenant)
        .getSingleResult();
    }
}