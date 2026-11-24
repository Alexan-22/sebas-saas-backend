
package com.tiendasebas.tiendasebas.config;

import com.tiendasebas.tiendasebas.entity.Tienda;
import com.tiendasebas.tiendasebas.repository.TiendaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class TenantResolver {

    private static final String ESTADO_ACTIVA = "ACTIVA";

    private final TiendaRepository tiendas;
    private final JdbcTemplate jdbc;

    @Value("${app.tenant.custom-domains-enabled:false}")
    private boolean customDomainsEnabled;

    @Value("${app.rls.auth-enabled:false}")
    private boolean rlsAuthEnabled;

    @Value("${app.tenant.base-domain:}")
    private String baseDomain;

    @Value("${app.tenant.local-subdomain:sebascollection}")
    private String localSubdomain;

    // Alias temporal y controlado para Azure staging.
    @Value("${app.tenant.staging-enabled:false}")
    private boolean stagingEnabled;

    @Value("${app.tenant.staging-host:}")
    private String stagingHost;

    @Value("${app.tenant.staging-subdomain:}")
    private String stagingSubdomain;

    // Selector PUBLICO opt-in. Nunca es una prueba de identidad/autorizacion.
    @Value("${app.tenant.staging-selector-enabled:false}")
    private boolean stagingSelectorEnabled;

    @Value("${app.tenant.staging-allowed-subdomains:}")
    private String stagingAllowedSubdomains;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    public TenantResolver(
            TiendaRepository tiendas,
            JdbcTemplate jdbc
    ) {
        this.tiendas = tiendas;
        this.jdbc = jdbc;
    }

    public Optional<Long> resolver(
            HttpServletRequest request
    ) {
        String host = request.getServerName();

        if (host == null) {
            return Optional.empty();
        }

        host = host.toLowerCase(Locale.ROOT);

        if (host.length() > 253 ||
                !host.matches(
                        "[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?"
                )) {
            return Optional.empty();
        }

        // Alias exclusivo para entorno de pruebas.
        // No funciona con RLS de autenticacion activo.
        // No se aplica al perfil de produccion.
        if (stagingEnabled &&
                !rlsAuthEnabled &&
                ("dev".equalsIgnoreCase(activeProfile) ||
                        "staging".equalsIgnoreCase(activeProfile)) &&
                !stagingHost.isBlank() &&
                host.equals(
                        stagingHost.trim()
                                .toLowerCase(Locale.ROOT)
                ) &&
                stagingSubdomain.matches(
                        "[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?"
                )) {

            // Un selector declarado por el navegador solo elige el contexto de
            // contenido; la autorizacion privada exige JWT con tid coincidente.
            // Si no se envia selector se conserva exactamente el alias anterior.
            var enviados = request.getHeaders("X-Storefront-Slug");
            if (enviados != null && enviados.hasMoreElements()) {
                String elegido = enviados.nextElement();
                if (enviados.hasMoreElements() || !stagingSelectorEnabled
                        || elegido == null
                        || !elegido.matches("[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?")) {
                    return Optional.empty();
                }
                boolean permitido = java.util.Arrays.stream(stagingAllowedSubdomains.split(","))
                        .map(String::trim)
                        .anyMatch(elegido::equals);
                if (!permitido) return Optional.empty();
                return buscarTiendaActiva(elegido);
            }
            return buscarTiendaActiva(stagingSubdomain);
        }

        // Resolver SQL seguro cuando RLS este activo.
        if (rlsAuthEnabled) {
            return Optional.ofNullable(
                    jdbc.queryForObject(
                            "SELECT public.auth_resolver_host(?,?,?)",
                            Long.class,
                            host,
                            baseDomain.trim().toLowerCase(Locale.ROOT),
                            customDomainsEnabled
                    )
            );
        }

        String subdominio;

        if (host.equals("localhost") ||
                host.equals("127.0.0.1") ||
                host.equals("::1")) {

            subdominio = localSubdomain;

        } else if (host.endsWith(".localhost")) {

            subdominio = host.substring(
                    0,
                    host.length() - ".localhost".length()
            );

            if (!subdominio.matches(
                    "[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?"
            )) {
                return Optional.empty();
            }

        } else {

            String base = baseDomain.trim()
                    .toLowerCase(Locale.ROOT);

            if (base.isEmpty() ||
                    !host.endsWith("." + base)) {
                return resolverDominioPersonalizado(host);
            }

            subdominio = host.substring(
                    0,
                    host.length() - base.length() - 1
            );

            if (!subdominio.matches(
                    "[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?"
            )) {
                return Optional.empty();
            }
        }

        return buscarTiendaActiva(subdominio);
    }

    private Optional<Long> buscarTiendaActiva(
            String subdominio
    ) {
        Optional<Tienda> encontrada =
                tiendas.findBySubdominio(subdominio);

        if (encontrada.isEmpty()) {
            return Optional.empty();
        }

        Tienda tienda = encontrada.get();

        if (!ESTADO_ACTIVA.equals(tienda.getEstado())) {
            return Optional.empty();
        }

        return Optional.ofNullable(tienda.getId());
    }

    private Optional<Long> resolverDominioPersonalizado(
            String host
    ) {
        if (!customDomainsEnabled ||
                host.length() > 253 ||
                !host.matches(
                        "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+" +
                                "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                )) {
            return Optional.empty();
        }

        var coincidencias = jdbc.queryForList(
                """
                SELECT d.tienda_id
                FROM public.dominios_saas d
                JOIN public.tiendas t
                  ON t.id = d.tienda_id
                WHERE d.dominio = ?
                  AND d.estado = 'VERIFICADO_DNS'
                  AND d.verificado_en IS NOT NULL
                  AND t.estado = 'ACTIVA'
                LIMIT 2
                """,
                Long.class,
                host
        );

        return coincidencias.size() == 1
                ? Optional.of(coincidencias.getFirst())
                : Optional.empty();
    }
}

