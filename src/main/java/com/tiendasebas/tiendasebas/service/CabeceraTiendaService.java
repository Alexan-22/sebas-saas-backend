package com.tiendasebas.tiendasebas.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CabeceraTiendaService {
    public record Submenu(String texto, String busqueda) {}

    public record MenuItem(String clave, String texto, boolean visible, List<Submenu> submenus) {
        public MenuItem(String clave, String texto, boolean visible) {
            this(clave, texto, visible, List.of());
        }
    }
    public record CabeceraConfig(boolean promoActiva, String promoModo, String promoVelocidad,
                                 List<String> promoMensajes, List<MenuItem> menuItems) {}
    private static final Set<String> CLAVES = Set.of("TODO", "MUJER", "HOMBRE", "NOVEDADES", "OFERTAS");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CabeceraTiendaService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public CabeceraConfig porDefecto() {
        return new CabeceraConfig(true, "ROTACION", "RAPIDA",
            List.of("NUEVAS COLECCIONES · DESCUBRE TU ESTILO"),
            List.of(new MenuItem("TODO","Todo",true), new MenuItem("MUJER","Mujer",true),
                    new MenuItem("HOMBRE","Hombre",true), new MenuItem("NOVEDADES","Novedades",true),
                    new MenuItem("OFERTAS","Ofertas",true)));
    }

    @Transactional(readOnly = true)
    public CabeceraConfig obtener() {
        Long tenant = TenantContext.getCurrentTenant();
        List<CabeceraConfig> encontrados = jdbc.query("""
            SELECT promo_activa, promo_modo, promo_velocidad, promo_mensajes::text AS mensajes,
                   menu_items::text AS menu
            FROM public.saas_cabecera_config WHERE tienda_id=?
            """, (rs, i) -> {
                try {
                    return new CabeceraConfig(rs.getBoolean("promo_activa"), rs.getString("promo_modo"), rs.getString("promo_velocidad"),
                        json.readValue(rs.getString("mensajes"), new TypeReference<List<String>>() {}),
                        json.readValue(rs.getString("menu"), new TypeReference<List<MenuItem>>() {}));
                } catch (Exception ex) {
                    throw new IllegalStateException("Configuracion de cabecera corrupta", ex);
                }
            }, tenant);
        return encontrados.isEmpty() ? porDefecto() : encontrados.get(0);
    }

    @Transactional
    public CabeceraConfig guardar(CabeceraConfig entrada) {
        CabeceraConfig valido = validar(entrada);
        try {
            jdbc.update("""
                INSERT INTO public.saas_cabecera_config
                  (tienda_id, promo_activa, promo_modo, promo_velocidad, promo_mensajes, menu_items)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                ON CONFLICT (tienda_id) DO UPDATE SET
                  promo_activa=EXCLUDED.promo_activa,
                  promo_modo=EXCLUDED.promo_modo,
                  promo_velocidad=EXCLUDED.promo_velocidad,
                  promo_mensajes=EXCLUDED.promo_mensajes,
                  menu_items=EXCLUDED.menu_items,
                  actualizado_en=NOW()
                """, TenantContext.getCurrentTenant(), valido.promoActiva(), valido.promoModo(), valido.promoVelocidad(),
                json.writeValueAsString(valido.promoMensajes()), json.writeValueAsString(valido.menuItems()));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar configuracion", ex);
        }
        return valido;
    }

    CabeceraConfig validar(CabeceraConfig c) {
        if (c == null || c.promoModo() == null || !Set.of("ROTACION", "DESPLAZAMIENTO").contains(c.promoModo()))
            throw invalido("Modo promocional no valido");
        String velocidad = "NORMAL".equals(c.promoVelocidad()) ? "RAPIDA" : c.promoVelocidad();
        if (velocidad == null || !Set.of("ESTATICA", "LENTA", "RAPIDA", "MUY_RAPIDA").contains(velocidad))
            throw invalido("Velocidad promocional no valida");
        if (c.promoMensajes() == null || c.promoMensajes().isEmpty() || c.promoMensajes().size() > 5)
            throw invalido("Indica entre 1 y 5 mensajes promocionales");
        List<String> mensajes = new ArrayList<>();
        for (String mensaje : c.promoMensajes()) {
            if (mensaje == null || mensaje.isBlank() || mensaje.strip().length() > 120)
                throw invalido("Cada mensaje debe tener entre 1 y 120 caracteres");
            mensajes.add(mensaje.strip());
        }
        if (c.menuItems() == null || c.menuItems().size() != CLAVES.size())
            throw invalido("El menu debe contener las cinco secciones permitidas");
        Set<String> usadas = new HashSet<>();
        List<MenuItem> menu = new ArrayList<>();
        for (MenuItem item : c.menuItems()) {
            if (item == null || item.clave() == null || !CLAVES.contains(item.clave()) || !usadas.add(item.clave()) ||
                item.texto() == null || item.texto().isBlank() || item.texto().strip().length() > 24)
                throw invalido("Secciones de menu invalidas o repetidas");
            List<Submenu> submenus = item.submenus() == null ? List.of() : item.submenus();
            if (submenus.size() > 8) {
                throw invalido("Cada menú admite hasta ocho submenús");
            }
            List<Submenu> validados = new ArrayList<>();
            for (Submenu submenu : submenus) {
                if (submenu == null || !textoValido(submenu.texto(), 24)
                        || !textoValido(submenu.busqueda(), 80)) {
                    throw invalido("Indica un nombre y una búsqueda válida para cada submenú");
                }
                validados.add(new Submenu(submenu.texto().strip(), submenu.busqueda().strip()));
            }
            menu.add(new MenuItem(item.clave(), item.texto().strip(), item.visible(), List.copyOf(validados)));
        }
        return new CabeceraConfig(c.promoActiva(), c.promoModo(), velocidad, List.copyOf(mensajes), List.copyOf(menu));
    }

    private boolean textoValido(String texto, int maximo) {
        return texto != null && !texto.isBlank() && texto.strip().length() <= maximo;
    }

    private ResponseStatusException invalido(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
