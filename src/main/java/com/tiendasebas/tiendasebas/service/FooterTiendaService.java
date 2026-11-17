package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FooterTiendaService {
    public record FooterConfig(String descripcion, String textoContacto, String horario, String correo,
                               String facebookUrl, String instagramUrl, String tiktokUrl,
                               boolean beneficiosActivos, String beneficioEnvios, String beneficioPagos,
                               String beneficioAtencion, boolean newsletterActivo, String newsletterTitulo,
                               String newsletterDescripcion, String textoLegal) {}

    private final JdbcTemplate jdbc;
    public FooterTiendaService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public FooterConfig porDefecto() {
        return new FooterConfig("Descubre prendas para expresar tu estilo, todos los días.",
            "Estamos para ayudarte a elegir tu próximo look.", "", "", "", "", "",
            true, "Envíos a todo el Perú", "Compras seguras", "Atención personalizada",
            false, "Novedades de nuestra tienda", "Síguenos en redes para conocer nuestras colecciones.",
            "Todos los derechos reservados.");
    }

    @Transactional(readOnly = true)
    public FooterConfig obtener() {
        Long tenantId = TenantContext.getCurrentTenant();
        List<FooterConfig> resultados = jdbc.query("""
            SELECT descripcion, texto_contacto, horario, correo, facebook_url, instagram_url, tiktok_url,
                   beneficios_activos, beneficio_envios, beneficio_pagos, beneficio_atencion,
                   newsletter_activo, newsletter_titulo, newsletter_descripcion, texto_legal
            FROM public.saas_footer_config WHERE tienda_id = ?
            """, (rs, n) -> new FooterConfig(
                rs.getString("descripcion"), rs.getString("texto_contacto"), rs.getString("horario"),
                rs.getString("correo"), rs.getString("facebook_url"), rs.getString("instagram_url"),
                rs.getString("tiktok_url"), rs.getBoolean("beneficios_activos"),
                rs.getString("beneficio_envios"), rs.getString("beneficio_pagos"),
                rs.getString("beneficio_atencion"), rs.getBoolean("newsletter_activo"),
                rs.getString("newsletter_titulo"), rs.getString("newsletter_descripcion"),
                rs.getString("texto_legal")), tenantId);
        return resultados.isEmpty() ? porDefecto() : resultados.get(0);
    }

    @Transactional
    public FooterConfig guardar(FooterConfig entrada) {
        FooterConfig v = validar(entrada);
        jdbc.update("""
            INSERT INTO public.saas_footer_config
            (tienda_id, descripcion, texto_contacto, horario, correo, facebook_url, instagram_url,
             tiktok_url, beneficios_activos, beneficio_envios, beneficio_pagos, beneficio_atencion,
             newsletter_activo, newsletter_titulo, newsletter_descripcion, texto_legal)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tienda_id) DO UPDATE SET
             descripcion=EXCLUDED.descripcion, texto_contacto=EXCLUDED.texto_contacto,
             horario=EXCLUDED.horario, correo=EXCLUDED.correo, facebook_url=EXCLUDED.facebook_url,
             instagram_url=EXCLUDED.instagram_url, tiktok_url=EXCLUDED.tiktok_url,
             beneficios_activos=EXCLUDED.beneficios_activos,
             beneficio_envios=EXCLUDED.beneficio_envios,
             beneficio_pagos=EXCLUDED.beneficio_pagos,
             beneficio_atencion=EXCLUDED.beneficio_atencion,
             newsletter_activo=EXCLUDED.newsletter_activo,
             newsletter_titulo=EXCLUDED.newsletter_titulo,
             newsletter_descripcion=EXCLUDED.newsletter_descripcion,
             texto_legal=EXCLUDED.texto_legal, actualizado_en=NOW()
            """, TenantContext.getCurrentTenant(), v.descripcion(), v.textoContacto(), v.horario(),
            v.correo(), v.facebookUrl(), v.instagramUrl(), v.tiktokUrl(), v.beneficiosActivos(),
            v.beneficioEnvios(), v.beneficioPagos(), v.beneficioAtencion(), v.newsletterActivo(),
            v.newsletterTitulo(), v.newsletterDescripcion(), v.textoLegal());
        return v;
    }

    FooterConfig validar(FooterConfig v) {
        if (v == null) throw invalido("Configuración requerida");
        String correo = campo(v.correo(), 150);
        if (!correo.isEmpty() && !correo.matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw invalido("Correo inválido");
        return new FooterConfig(
            campo(v.descripcion(), 240), campo(v.textoContacto(), 150), campo(v.horario(), 100), correo,
            url(v.facebookUrl(), "facebook.com", "fb.com"),
            url(v.instagramUrl(), "instagram.com"), url(v.tiktokUrl(), "tiktok.com"),
            v.beneficiosActivos(), campo(v.beneficioEnvios(), 90), campo(v.beneficioPagos(), 90),
            campo(v.beneficioAtencion(), 90), v.newsletterActivo(),
            campo(v.newsletterTitulo(), 100), campo(v.newsletterDescripcion(), 200), campo(v.textoLegal(), 180));
    }

    private String campo(String v, int max) {
        String s = v == null ? "" : v.strip();
        if (s.length() > max) throw invalido("Texto demasiado largo; máximo " + max + " caracteres");
        return s;
    }

    private String url(String entrada, String... dominios) {
        String s = campo(entrada, 350);
        if (s.isEmpty()) return "";
        try {
            URI u = URI.create(s);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null ||
                u.getUserInfo() != null || u.getPort() != -1) throw invalido("URL social inválida");
            String h = u.getHost().toLowerCase(Locale.ROOT);
            for (String dominio : dominios)
                if (h.equals(dominio) || h.equals("www." + dominio)) return s;
        } catch (IllegalArgumentException ex) { throw invalido("URL social inválida: usa https y el dominio oficial"); }
        throw invalido("La URL no pertenece a la red social indicada");
    }

    private ResponseStatusException invalido(String s) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
    }
}
