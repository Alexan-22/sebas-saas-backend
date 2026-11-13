
package com.tiendasebas.tiendasebas.config;

import com.tiendasebas.tiendasebas.entity.Tienda;
import com.tiendasebas.tiendasebas.repository.TiendaRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

// ============================================
// SEBAS COLLECTION SAAS V2
// RESOLUCION Y ESTADO DE TIENDAS
// ============================================

@Component
public class TenantResolver {

    // ========================================
    // DEPENDENCIAS
    // ========================================

    private final TiendaRepository tiendas;

    // Dominio principal de la plataforma.
    // Ejemplo: plataforma.com

    @Value("${app.tenant.base-domain:}")
    private String baseDomain;

    // Tienda utilizada en desarrollo local.

    @Value("${app.tenant.local-subdomain:sebascollection}")
    private String localSubdomain;

    // ========================================
    // ESTADO PERMITIDO
    // ========================================

    private static final String ESTADO_ACTIVA = "ACTIVA";

    // ========================================
    // CONSTRUCTOR
    // ========================================

    public TenantResolver(
            TiendaRepository tiendas
    ) {
        this.tiendas = tiendas;
    }

    // ========================================
    // RESOLVER TIENDA
    // ========================================

    public Optional<Long> resolver(
            HttpServletRequest request
    ) {

        // Obtener host de la solicitud.

        String host = request
                .getServerName()
                .toLowerCase(Locale.ROOT);

        String subdominio;

        // ====================================
        // ENTORNO LOCAL
        // ====================================

        if (
                host.equals("localhost") ||
                host.equals("127.0.0.1") ||
                host.equals("::1")
        ) {

            // Compatibilidad con la configuracion actual.
            subdominio = localSubdomain;

        } else if (host.endsWith(".localhost")) {

            // Desarrollo: un solo servidor para varias tiendas.
            String prefijoLocal = host.substring(
                    0,
                    host.length() - ".localhost".length()
            );

            // Solo un subdominio sencillo y valido.
            if (!prefijoLocal.matches(
                    "[a-z0-9](?:[a-z0-9-]{0,48}[a-z0-9])?"
            )) {
                return Optional.empty();
            }

            subdominio = prefijoLocal;

        } else {

            // =================================
            // DOMINIO DE PRODUCCION
            // =================================

            String base = baseDomain
                    .trim()
                    .toLowerCase(Locale.ROOT);

            // Aceptar solo subdominios dentro
            // del dominio oficial configurado.

            if (
                    base.isEmpty() ||
                            !host.endsWith("." + base)
            ) {
                return Optional.empty();
            }

            // Extraer el subdominio.

            String prefijo = host.substring(
                    0,
                    host.length() - base.length() - 1
            );

            // Evitar hosts que no cumplen
            // la estructura esperada.

            if (!prefijo.matches("[a-z0-9-]{1,50}")) {
                return Optional.empty();
            }

            subdominio = prefijo;
        }

        // ====================================
        // CONSULTAR TIENDA EN SUPABASE
        // ====================================

        Optional<Tienda> tiendaEncontrada =
                tiendas.findBySubdominio(
                        subdominio
                );

        // La tienda no existe.

        if (tiendaEncontrada.isEmpty()) {
            return Optional.empty();
        }

        Tienda tienda = tiendaEncontrada.get();

        // ====================================
        // VERIFICAR ESTADO REAL
        // ====================================

        // Solo una tienda ACTIVA puede
        // continuar hacia sus endpoints
        // operativos normales.
        //
        // PENDIENTE y SUSPENDIDA no reciben
        // un identificador de tenant válido.

        if (!ESTADO_ACTIVA.equals(tienda.getEstado())) {
            return Optional.empty();
        }

        // ====================================
        // TIENDA AUTORIZADA
        // ====================================

        return Optional.ofNullable(
                tienda.getId()
        );
    }
}
