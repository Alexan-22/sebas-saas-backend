package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.TiendaSebasApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Diagnoses the real application, never the commercial datasource. */
class IsolatedApplicationStartupTest {
    @Test void fullApplicationStartsOnlyOnDisposablePostgres() throws Exception {
        PostgresTestSafety.rejectExternalConfiguration(System.getenv());
        PostgresTestSafety.rejectExternalConfiguration(System.getProperties());
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("startup_diagnostic").withUsername("test")
                .withPassword(UUID.randomUUID().toString())) {
            postgres.start();
            String url=PostgresTestSafety.verifiedUrl(postgres);
            var environment=new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().addFirst(new MapPropertySource("container-only",Map.of(
                "spring.datasource.url",url,"spring.datasource.username",postgres.getUsername(),
                "spring.datasource.password",postgres.getPassword(),"app.jwt.secret",UUID.randomUUID().toString()+UUID.randomUUID(),
                "app.upload.dir",Files.createTempDirectory("sebas-startup-uploads-").toString())));
            var application=new SpringApplication(TiendaSebasApplication.class);
            application.setEnvironment(environment);
            application.addInitializers(context -> {
                // Exclude other test fixtures from the real application's component scan.
                context.getBeanFactory().registerSingleton("isolatedTestClasspathFilter", new org.springframework.boot.context.TypeExcludeFilter() {
                    @Override public boolean match(org.springframework.core.type.classreading.MetadataReader reader,
                            org.springframework.core.type.classreading.MetadataReaderFactory factory) {
                        return reader.getClassMetadata().getClassName().startsWith("com.tiendasebas.tiendasebas.integration.");
                    }
                });
                if (!url.equals(context.getEnvironment().getProperty("spring.datasource.url"))
                        || !postgres.getUsername().equals(context.getEnvironment().getProperty("spring.datasource.username"))
                        || !postgres.getPassword().equals(context.getEnvironment().getProperty("spring.datasource.password")))
                    throw new IllegalStateException("Datasource externo rechazado antes del arranque");
            });
            try(var context=application.run(
                "--spring.config.location=optional:classpath:/isolated-startup-diagnostic/",
                "--spring.profiles.active=test,payments-sandbox","--payments.sandbox.enabled=true",
                "--server.address=127.0.0.1","--server.port=0",
                "--spring.servlet.multipart.max-file-size=6MB","--spring.servlet.multipart.max-request-size=7MB",
                "--spring.jpa.hibernate.ddl-auto=create-drop","--spring.jpa.open-in-view=false",
                "--spring.sql.init.mode=never","--spring.flyway.enabled=false","--spring.liquibase.enabled=false",
                "--app.storage.provider=local","--app.jwt.expiration=3600000",
                "--app.cors.allowed-origins=http://fixture.example.test","--app.tenant.base-domain=saas.test")) {
                assertTrue(((WebServerApplicationContext)context).getWebServer().getPort()>0);
                var source=context.getBean(javax.sql.DataSource.class);
                try(var connection=source.getConnection()) { assertEquals(url,connection.getMetaData().getURL()); }
                new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new org.springframework.core.io.ClassPathResource("db/migration/V4__pagos_simulados_persistentes.sql")).execute(source);
                assertNotNull(context.getBean(com.tiendasebas.tiendasebas.pagos.PaymentAdminController.class));
                var chain=context.getBean(org.springframework.security.web.FilterChainProxy.class);
                assertEquals(1,chain.getFilterChains().stream().flatMap(c -> c.getFilters().stream())
                    .filter(f -> f instanceof com.tiendasebas.tiendasebas.security.JwtAuthenticationFilter).count());
                assertFalse(chain.getFilterChains().stream().flatMap(c -> c.getFilters().stream())
                    .anyMatch(f -> f instanceof org.springframework.security.web.authentication.www.BasicAuthenticationFilter
                        || f instanceof org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter));
                IsolatedAdminBrowserSession.verify(context, ((WebServerApplicationContext)context).getWebServer().getPort());
            }
        }
    }
}
