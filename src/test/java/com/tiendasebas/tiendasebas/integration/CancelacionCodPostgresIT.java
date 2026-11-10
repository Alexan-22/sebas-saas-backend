package com.tiendasebas.tiendasebas.integration;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.controller.CancelacionCodController;
import com.tiendasebas.tiendasebas.entity.DetalleVenta;
import com.tiendasebas.tiendasebas.entity.EstadoPedido;
import com.tiendasebas.tiendasebas.entity.ProductoTalla;
import com.tiendasebas.tiendasebas.entity.TipoVenta;
import com.tiendasebas.tiendasebas.entity.Venta;
import com.tiendasebas.tiendasebas.repository.DetalleVentaRepository;
import com.tiendasebas.tiendasebas.repository.ProductoTallaRepository;
import com.tiendasebas.tiendasebas.repository.VentaRepository;
import com.tiendasebas.tiendasebas.service.CancelacionCodService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FASE 3D, INTEGRACION ACOTADA REAL:
 *  - Ejecuta las CLASES REALES CancelacionCodService y CancelacionCodController.
 *  - Usa PostgreSQL 17 efimero con transacciones, locks FOR UPDATE y restricciones SQL.
 *  - Repositorios de PRUEBA: adaptadores Mockito que persisten en el MISMO PostgreSQL
 *    via JDBC. No comprueba implementaciones JPA reales ni la resolucion JWT/host HTTP.
 *  - No inicia la aplicacion ni lee application-dev.properties / DB_URL / Supabase.
 */
@Testcontainers
class CancelacionCodPostgresIT {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("fase3d_cod_aislada")
            .withUsername("sebas_test")
            .withPassword("solo_testcontainers_local");

    static AnnotationConfigApplicationContext context;
    static JdbcTemplate db;
    static CancelacionCodController controller;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableMethodSecurity
    static class ContextoAislado {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(PostgresTestSafety.verifiedUrl(postgres),
                    postgres.getUsername(), postgres.getPassword());
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }

        @Bean
        PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        // Adaptadores de repositorio: sus SELECT FOR UPDATE y UPDATE son SQL real.
        // Mantienen el servicio original sin iniciar Hibernate ni tocar Supabase.
        @Bean
        VentaRepository ventaRepository(JdbcTemplate jdbc) {
            VentaRepository repo = mock(VentaRepository.class);
            when(repo.buscarPorIdYTiendaConBloqueo(anyInt(), anyLong())).thenAnswer(call -> {
                Integer id = call.getArgument(0);
                Long tienda = call.getArgument(1);
                List<Venta> ventas = jdbc.query("""
                        SELECT id_venta, tienda_id, tipo_venta, medio_pago, estado_pedido, estado
                          FROM public.ventas
                         WHERE id_venta=? AND tienda_id=? FOR UPDATE
                        """, (rs, n) -> {
                    Venta v = new Venta();
                    v.setIdVenta(rs.getInt("id_venta"));
                    v.setTiendaId(rs.getLong("tienda_id"));
                    v.setTipoVenta(TipoVenta.valueOf(rs.getString("tipo_venta")));
                    v.setMedioPago(rs.getString("medio_pago"));
                    v.setEstadoPedido(EstadoPedido.valueOf(rs.getString("estado_pedido")));
                    v.setEstado(rs.getString("estado"));
                    return v;
                }, id, tienda);
                return ventas.stream().findFirst();
            });
            when(repo.saveAndFlush(any(Venta.class))).thenAnswer(call -> {
                Venta v = call.getArgument(0);
                int cambios = jdbc.update("""
                        UPDATE public.ventas SET estado_pedido=?, estado=?
                         WHERE id_venta=? AND tienda_id=?
                        """, v.getEstadoPedido().name(), v.getEstado(), v.getIdVenta(), v.getTiendaId());
                if (cambios != 1) throw new IllegalStateException("Venta de prueba no actualizada");
                return v;
            });
            return repo;
        }

        @Bean
        DetalleVentaRepository detalleVentaRepository(JdbcTemplate jdbc) {
            DetalleVentaRepository repo = mock(DetalleVentaRepository.class);
            when(repo.findByVentaOrderByIdDetalleVentaAsc(any(Venta.class))).thenAnswer(call -> {
                Venta venta = call.getArgument(0);
                return jdbc.query("""
                        SELECT id_producto_talla, cantidad FROM public.detalle_venta
                         WHERE id_venta=? ORDER BY id_detalle_venta
                        """, (rs, n) -> {
                    ProductoTalla variante = new ProductoTalla();
                    variante.setIdProductoTalla(rs.getInt("id_producto_talla"));
                    DetalleVenta detalle = new DetalleVenta();
                    detalle.setProductoTalla(variante);
                    detalle.setCantidad(rs.getInt("cantidad"));
                    return detalle;
                }, venta.getIdVenta());
            });
            return repo;
        }

        @Bean
        ProductoTallaRepository productoTallaRepository(JdbcTemplate jdbc) {
            ProductoTallaRepository repo = mock(ProductoTallaRepository.class);
            when(repo.buscarPorIdConBloqueo(anyInt(), anyLong())).thenAnswer(call -> {
                Integer id = call.getArgument(0);
                Long tienda = call.getArgument(1);
                List<ProductoTalla> variantes = jdbc.query("""
                        SELECT id_producto_talla, stock FROM public.producto_tallas
                         WHERE id_producto_talla=? AND tienda_id=? FOR UPDATE
                        """, (rs, n) -> {
                    ProductoTalla variante = new ProductoTalla();
                    variante.setIdProductoTalla(rs.getInt("id_producto_talla"));
                    variante.setStock(rs.getInt("stock"));
                    return variante;
                }, id, tienda);
                return variantes.stream().findFirst();
            });
            when(repo.save(any(ProductoTalla.class))).thenAnswer(call -> {
                ProductoTalla v = call.getArgument(0);
                int cambios = jdbc.update("""
                        UPDATE public.producto_tallas SET stock=? WHERE id_producto_talla=?
                        """, v.getStock(), v.getIdProductoTalla());
                if (cambios != 1) throw new IllegalStateException("Stock de prueba no actualizado");
                return v;
            });
            return repo;
        }

        @Bean
        CancelacionCodService cancelacionCodService(VentaRepository ventas,
                DetalleVentaRepository detalles, ProductoTallaRepository inventario, JdbcTemplate jdbc) {
            return new CancelacionCodService(ventas, detalles, inventario, jdbc);
        }

        @Bean
        CancelacionCodController cancelacionCodController(CancelacionCodService service) {
            return new CancelacionCodController(service);
        }
    }

    @BeforeAll
    static void iniciarPostgresYContextoAislado() {
        assertTrue(postgres.isRunning(), "Testcontainers no inicio PostgreSQL");
        context = new AnnotationConfigApplicationContext(ContextoAislado.class);
        db = context.getBean(JdbcTemplate.class);
        controller = context.getBean(CancelacionCodController.class);
        crearEsquemaDePruebas();
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V2__kardex_cancelaciones_cod.sql"))
                .execute(context.getBean(DataSource.class));
        assertEquals("fase3d_cod_aislada", db.queryForObject("SELECT current_database()", String.class));
    }

    static void crearEsquemaDePruebas() {
        db.execute("CREATE TABLE public.tiendas (id BIGINT PRIMARY KEY)");
        db.execute("""
                CREATE TABLE public.ventas (
                    id_venta INTEGER PRIMARY KEY,
                    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
                    tipo_venta VARCHAR(20) NOT NULL,
                    medio_pago VARCHAR(50) NOT NULL,
                    estado_pedido VARCHAR(30) NOT NULL,
                    estado VARCHAR(50) NOT NULL
                )
                """);
        db.execute("""
                CREATE TABLE public.producto_tallas (
                    id_producto_talla INTEGER PRIMARY KEY,
                    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
                    stock INTEGER NOT NULL CHECK (stock >= 0)
                )
                """);
        db.execute("""
                CREATE TABLE public.detalle_venta (
                    id_detalle_venta INTEGER PRIMARY KEY,
                    id_venta INTEGER NOT NULL REFERENCES public.ventas(id_venta),
                    id_producto_talla INTEGER NOT NULL REFERENCES public.producto_tallas(id_producto_talla),
                    cantidad INTEGER NOT NULL CHECK (cantidad > 0)
                )
                """);
        db.execute("""
                CREATE TABLE public.saas_cobros_contraentrega (
                    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
                    id_venta INTEGER NOT NULL REFERENCES public.ventas(id_venta),
                    estado VARCHAR(30) NOT NULL,
                    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
                    PRIMARY KEY (tienda_id,id_venta)
                )
                """);
        db.execute("""
                CREATE TABLE public.saas_envios (
                    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
                    id_venta INTEGER NOT NULL REFERENCES public.ventas(id_venta),
                    operador VARCHAR(30) NOT NULL,
                    modalidad VARCHAR(30) NOT NULL,
                    estado VARCHAR(30) NOT NULL,
                    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
                    PRIMARY KEY (tienda_id,id_venta)
                )
                """);
        db.execute("""
                CREATE TABLE public.saas_cancelaciones_cod (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    tienda_id BIGINT NOT NULL REFERENCES public.tiendas(id),
                    id_venta INTEGER NOT NULL REFERENCES public.ventas(id_venta),
                    motivo VARCHAR(300) NOT NULL CHECK(length(trim(motivo)) BETWEEN 10 AND 300),
                    cancelado_por VARCHAR(200) NOT NULL,
                    unidades_repuestas INTEGER NOT NULL CHECK(unidades_repuestas > 0),
                    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
                    CONSTRAINT saas_cancelaciones_cod_unicas UNIQUE (tienda_id,id_venta)
                )
                """);
        // Mismo control de pertenencia del SQL instalado en fase 3D.
        db.execute("""
                CREATE OR REPLACE FUNCTION public.saas_cancelacion_cod_validar_tienda()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NOT EXISTS (
                     SELECT 1 FROM public.ventas v
                      WHERE v.tienda_id = NEW.tienda_id
                        AND v.id_venta = NEW.id_venta
                        AND v.tipo_venta = 'ONLINE'
                        AND v.medio_pago = 'CONTRA_ENTREGA'
                  ) THEN
                     RAISE EXCEPTION 'No coincide tienda, venta online y pago COD';
                  END IF;
                  RETURN NEW;
                END;
                $$
                """);
        db.execute("""
                CREATE TRIGGER saas_cancelacion_cod_validar_tienda_trg
                BEFORE INSERT OR UPDATE OF tienda_id,id_venta
                ON public.saas_cancelaciones_cod FOR EACH ROW
                EXECUTE FUNCTION public.saas_cancelacion_cod_validar_tienda()
                """);
    }

    @BeforeEach
    void datosDePrueba() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        db.execute("DELETE FROM public.inventario_kardex");
        db.execute("DELETE FROM public.saas_cancelaciones_cod");
        db.execute("DELETE FROM public.saas_envios");
        db.execute("DELETE FROM public.saas_cobros_contraentrega");
        db.execute("DELETE FROM public.detalle_venta");
        db.execute("DELETE FROM public.producto_tallas");
        db.execute("DELETE FROM public.ventas");
        db.execute("DELETE FROM public.tiendas");
        db.update("INSERT INTO public.tiendas(id) VALUES (1),(2)");
        db.update("INSERT INTO public.ventas(id_venta,tienda_id,tipo_venta,medio_pago,estado_pedido,estado) VALUES (101,1,'ONLINE','CONTRA_ENTREGA','RECIBIDO','REGISTRADA')");
        db.update("INSERT INTO public.producto_tallas(id_producto_talla,tienda_id,stock) VALUES(501,1,18),(502,2,20)");
        db.update("INSERT INTO public.detalle_venta(id_detalle_venta,id_venta,id_producto_talla,cantidad) VALUES(601,101,501,2)");
        db.update("INSERT INTO public.saas_cobros_contraentrega(tienda_id,id_venta,estado) VALUES(1,101,'PENDIENTE')");
        db.update("INSERT INTO public.saas_envios(tienda_id,id_venta,operador,modalidad,estado) VALUES(1,101,'PROPIO','DOMICILIO','PENDIENTE')");
    }

    @AfterEach
    void limpiarHilos() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void cerrar() {
        if (context != null) context.close();
    }

    private static Authentication autenticar(String correo, Long tienda, String rol) {
        Authentication auth = new UsernamePasswordAuthenticationToken(correo, "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.setCurrentTenant(tienda);
        return auth;
    }

    private CancelacionCodService.Resultado cancelar(String correo, Long tienda, String rol) {
        Authentication auth = autenticar(correo, tienda, rol);
        return controller.cancelar(101,
                new CancelacionCodController.MotivoCancelacion("Cancelacion controlada de integracion"),
                auth).getBody();
    }

    private int stock(Integer idVariante) {
        return db.queryForObject("SELECT stock FROM public.producto_tallas WHERE id_producto_talla=?",
                Integer.class, idVariante);
    }

    private int auditoria() {
        return db.queryForObject("SELECT count(*) FROM public.saas_cancelaciones_cod WHERE tienda_id=1 AND id_venta=101", Integer.class);
    }

    private String estado(String tabla) {
        if (!List.of("ventas", "saas_envios", "saas_cobros_contraentrega").contains(tabla)) {
            throw new IllegalArgumentException("Tabla no permitida");
        }
        String columna = "ventas".equals(tabla) ? "estado_pedido" : "estado";
        return db.queryForObject("SELECT " + columna + " FROM public." + tabla + " WHERE id_venta=101", String.class);
    }

    @Test
    void reponerStockRealConTransaccionYBitacora() {
        assertEquals(18, stock(501));
        CancelacionCodService.Resultado r = cancelar("admin1@pruebas.local", 1L, "ADMIN");
        assertNotNull(r);
        assertEquals(2, r.unidadesRepuestas());
        assertEquals(20, stock(501));
        assertEquals(1, auditoria());
        assertEquals("CANCELADO", estado("ventas"));
        assertEquals("CANCELADO", estado("saas_envios"));
        assertEquals("CANCELADO", estado("saas_cobros_contraentrega"));
        assertEquals(20, stock(502)); // No afecta la otra tienda.
    }

    @Test
    void cancelarDosVecesNoDuplicaInventario() {
        cancelar("admin1@pruebas.local", 1L, "ADMIN");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cancelar("admin1@pruebas.local", 1L, "ADMIN"));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals(20, stock(501));
        assertEquals(1, auditoria());
    }

    @Test
    @Timeout(45)
    void dosCancelacionesSimultaneasUnaSolaReponeStock() throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futuros = new ArrayList<>();
            for (int i=0; i<2; i++) {
                final int intento = i;
                futuros.add(pool.submit(() -> {
                    salida.await();
                    try {
                        cancelar("admin" + intento + "@pruebas.local", 1L, "ADMIN");
                        return "APROBADA";
                    } catch (ResponseStatusException ex) {
                        if (ex.getStatusCode().value() == 409) return "CONFLICTO_409";
                        throw ex;
                    } finally {
                        SecurityContextHolder.clearContext();
                        TenantContext.clear();
                    }
                }));
            }
            salida.countDown();
            List<String> resultados = new ArrayList<>();
            for (Future<String> f : futuros) resultados.add(f.get(30, TimeUnit.SECONDS));
            assertEquals(1, resultados.stream().filter("APROBADA"::equals).count());
            assertEquals(1, resultados.stream().filter("CONFLICTO_409"::equals).count());
            assertEquals(20, stock(501));
            assertEquals(1, auditoria());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void adminDeTiendaDosNoPuedeCancelarPedidoDeTiendaUno() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cancelar("admin2@pruebas.local", 2L, "ADMIN"));
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
        assertEquals(18, stock(501));
        assertEquals(20, stock(502));
        assertEquals(0, auditoria());
        assertEquals("RECIBIDO", estado("ventas"));
    }

    @Test
    void usuarioSinRolAdminNoPuedeCancelar() {
        assertThrows(AccessDeniedException.class,
                () -> cancelar("cliente1@pruebas.local", 1L, "USER"));
        assertEquals(18, stock(501));
        assertEquals(0, auditoria());
    }

    @Test
    void rollbackSiStockNoSePuedeIncrementar() {
        db.update("UPDATE public.producto_tallas SET stock=? WHERE id_producto_talla=501", Integer.MAX_VALUE);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cancelar("admin1@pruebas.local", 1L, "ADMIN"));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals(Integer.MAX_VALUE, stock(501));
        assertEquals(0, auditoria()); // INSERT auditado se revirtio con la transaccion.
        assertEquals("PENDIENTE", estado("saas_cobros_contraentrega"));
        assertEquals("PENDIENTE", estado("saas_envios"));
        assertEquals("RECIBIDO", estado("ventas"));
    }
}
