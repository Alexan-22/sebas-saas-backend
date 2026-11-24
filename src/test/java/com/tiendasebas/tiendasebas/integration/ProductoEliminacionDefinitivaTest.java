package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.service.ProductoEliminacionDefinitivaService;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ProductoEliminacionDefinitivaTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("productos_test")
                    .withUsername("test_user")
                    .withPassword("solo_test_temporal");

    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static ProductoEliminacionDefinitivaService servicio;

    @BeforeAll
    static void prepararBase() {
        String url = PostgresTestSafety.verifiedUrl(postgres);

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(url);
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());

        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        servicio = new ProductoEliminacionDefinitivaService(jdbc);

        jdbc.execute("""
            CREATE TABLE public.productos (
                id_producto INTEGER PRIMARY KEY,
                tienda_id BIGINT NOT NULL
            )
            """);

        jdbc.execute("""
            CREATE TABLE public.producto_tallas (
                id_producto_talla INTEGER PRIMARY KEY,
                id_producto INTEGER NOT NULL
                    REFERENCES public.productos(id_producto)
            )
            """);

        jdbc.execute("""
            CREATE TABLE public.producto_imagenes (
                id_imagen INTEGER PRIMARY KEY,
                id_producto INTEGER NOT NULL
                    REFERENCES public.productos(id_producto)
            )
            """);

        jdbc.execute("""
            CREATE TABLE public.saas_b2b_escalas (
                id_escala INTEGER PRIMARY KEY,
                id_producto INTEGER NOT NULL
                    REFERENCES public.productos(id_producto)
            )
            """);

        jdbc.execute("""
            CREATE TABLE public.saas_producto_colores_ocultos (
                id_color INTEGER PRIMARY KEY,
                id_producto INTEGER NOT NULL
                    REFERENCES public.productos(id_producto)
                    ON DELETE CASCADE
            )
            """);

        jdbc.execute("""
            CREATE TABLE public.detalle_venta (
                id_detalle INTEGER PRIMARY KEY,
                id_producto_talla INTEGER NOT NULL
                    REFERENCES public.producto_tallas(id_producto_talla)
            )
            """);
    }

    @BeforeEach
    void prepararDatos() {
        TenantContext.clear();

        jdbc.execute("""
            DROP TRIGGER IF EXISTS impedir_borrado
            ON public.producto_tallas
            """);

        jdbc.execute("""
            TRUNCATE TABLE
                public.detalle_venta,
                public.saas_producto_colores_ocultos,
                public.producto_imagenes,
                public.saas_b2b_escalas,
                public.producto_tallas,
                public.productos
            CASCADE
            """);

        jdbc.update("""
            INSERT INTO public.productos(id_producto, tienda_id)
            VALUES (100, 1), (200, 2)
            """);

        jdbc.update("""
            INSERT INTO public.producto_tallas
                (id_producto_talla, id_producto)
            VALUES (1000, 100), (2000, 200)
            """);

        jdbc.update("""
            INSERT INTO public.producto_imagenes
                (id_imagen, id_producto)
            VALUES (1, 100)
            """);

        jdbc.update("""
            INSERT INTO public.saas_b2b_escalas
                (id_escala, id_producto)
            VALUES (1, 100)
            """);

        jdbc.update("""
            INSERT INTO public.saas_producto_colores_ocultos
                (id_color, id_producto)
            VALUES (1, 100)
            """);
    }

    @AfterEach
    void limpiarTenant() {
        TenantContext.clear();
    }

    private int contar(String tabla) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM public." + tabla,
                Integer.class
        );
    }

    @Test
    void eliminaProductoSinHistorial() {
        TenantContext.setCurrentTenant(1L);

        tx.executeWithoutResult(
                status -> servicio.eliminarDefinitivamente(100)
        );

        assertEquals(1, contar("productos"));
        assertEquals(1, contar("producto_tallas"));
        assertEquals(0, contar("producto_imagenes"));
        assertEquals(0, contar("saas_b2b_escalas"));
        assertEquals(0, contar("saas_producto_colores_ocultos"));

        // El producto de la otra tienda permanece.
        assertEquals(1, jdbc.queryForObject("""
            SELECT COUNT(*) FROM public.productos
            WHERE id_producto = 200 AND tienda_id = 2
            """, Integer.class));
    }

    @Test
    void bloqueaProductoConVenta() {
        TenantContext.setCurrentTenant(1L);

        jdbc.update("""
            INSERT INTO public.detalle_venta
                (id_detalle, id_producto_talla)
            VALUES (1, 1000)
            """);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> tx.executeWithoutResult(
                        status -> servicio.eliminarDefinitivamente(100)
                )
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(2, contar("productos"));
        assertEquals(1, contar("detalle_venta"));
        assertEquals(1, contar("producto_imagenes"));
    }

    @Test
    void impideEliminarProductoDeOtraTienda() {
        TenantContext.setCurrentTenant(2L);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> tx.executeWithoutResult(
                        status -> servicio.eliminarDefinitivamente(100)
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals(2, contar("productos"));
        assertEquals(1, contar("producto_imagenes"));
        assertEquals(2, contar("producto_tallas"));
    }

    @Test
    void revierteCambiosAnteErrorIntermedio() {
        TenantContext.setCurrentTenant(1L);

        // Forzar una falla despues de intentar
        // borrar las imagenes y las escalas.
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION public.fallar_borrado_test()
            RETURNS trigger
            LANGUAGE plpgsql
            AS $$
            BEGIN
                RAISE EXCEPTION 'Fallo simulado para rollback';
            END;
            $$
            """);

        jdbc.execute("""
            CREATE TRIGGER impedir_borrado
            BEFORE DELETE ON public.producto_tallas
            FOR EACH ROW
            EXECUTE FUNCTION public.fallar_borrado_test()
            """);

        assertThrows(
                DataAccessException.class,
                () -> tx.executeWithoutResult(
                        status -> servicio.eliminarDefinitivamente(100)
                )
        );

        // La transaccion debe restaurarlo todo.
        assertEquals(2, contar("productos"));
        assertEquals(2, contar("producto_tallas"));
        assertEquals(1, contar("producto_imagenes"));
        assertEquals(1, contar("saas_b2b_escalas"));
        assertEquals(1, contar("saas_producto_colores_ocultos"));
    }
}
