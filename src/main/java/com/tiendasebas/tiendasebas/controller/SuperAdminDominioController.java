
package com.tiendasebas.tiendasebas.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/superadmin/dominios")
public class SuperAdminDominioController {

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    private static final Pattern DOMINIO = Pattern.compile(
            "^(?=.{4,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$"
    );

    public SuperAdminDominioController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Solicitud(Long tiendaId, String nombre) {}

    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbc.queryForList("""
            SELECT d.id, d.tienda_id, t.nombre AS tienda,
                   d.dominio AS nombre, d.estado,
                   d.token_verificacion, d.creado_en,
                   d.actualizado_en, d.verificado_en
            FROM public.dominios_saas d
            JOIN public.tiendas t ON t.id = d.tienda_id
            ORDER BY d.creado_en DESC, d.id DESC
        """);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> crear(@RequestBody Solicitud solicitud) {
        validarSolicitud(solicitud);
        String dominio = normalizar(solicitud.nombre());
        String token = generarToken();

        try {
            Long id = jdbc.queryForObject("""
                INSERT INTO public.dominios_saas
                    (tienda_id, dominio, token_verificacion)
                VALUES (?, ?, ?)
                RETURNING id
            """, Long.class, solicitud.tiendaId(), dominio, token);

            return buscar(id);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El dominio ya existe o la tienda no es válida.");
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> actualizar(
            @PathVariable Long id,
            @RequestBody Solicitud solicitud
    ) {
        validarSolicitud(solicitud);
        buscar(id);

        try {
            jdbc.update("""
                UPDATE public.dominios_saas
                SET tienda_id = ?, dominio = ?, estado = 'PENDIENTE',
                    token_verificacion = ?, verificado_en = NULL,
                    actualizado_en = NOW()
                WHERE id = ?
            """, solicitud.tiendaId(), normalizar(solicitud.nombre()),
                    generarToken(), id);

            return buscar(id);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dominio duplicado o tienda no válida.");
        }
    }

    @PostMapping("/{id}/verificar")
    @Transactional
    public Map<String, Object> verificar(@PathVariable Long id) {
        Map<String, Object> dominio = buscar(id);
        String nombre = dominio.get("nombre").toString();
        String token = dominio.get("token_verificacion").toString();

        if (!verificarTxt(nombre, token)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se encontró el registro TXT de verificación.");
        }

        int actualizados = jdbc.update("""
            UPDATE public.dominios_saas
            SET estado = 'VERIFICADO_DNS',
                verificado_en = NOW(),
                actualizado_en = NOW()
            WHERE id = ? AND dominio = ? AND token_verificacion = ?
        """, id, nombre, token);
        if (actualizados != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El dominio cambió durante la verificación");
        }

        return buscar(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void eliminar(@PathVariable Long id) {
        int filas = jdbc.update(
                "DELETE FROM public.dominios_saas WHERE id = ?", id
        );

        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Dominio no encontrado.");
        }
    }

    private Map<String, Object> buscar(Long id) {
        try {
            return jdbc.queryForMap("""
                SELECT d.id, d.tienda_id, t.nombre AS tienda,
                       d.dominio AS nombre, d.estado,
                       d.token_verificacion, d.creado_en,
                       d.actualizado_en, d.verificado_en
                FROM public.dominios_saas d
                JOIN public.tiendas t ON t.id = d.tienda_id
                WHERE d.id = ?
            """, id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Dominio no encontrado.");
        }
    }

    private void validarSolicitud(Solicitud solicitud) {
        if (solicitud == null || solicitud.tiendaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona una tienda.");
        }

        if (solicitud.nombre() == null ||
                !DOMINIO.matcher(normalizar(solicitud.nombre())).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El dominio no tiene un formato válido.");
        }
    }

    private String normalizar(String valor) {
        return valor.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceFirst("\\.$", "");
    }

    private String generarToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "sebas-saas-" + java.util.HexFormat.of().formatHex(bytes);
    }

    private boolean verificarTxt(String dominio, String token) {
        Hashtable<String, String> entorno = new Hashtable<>();
        entorno.put(DirContext.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.dns.DnsContextFactory");
        entorno.put("com.sun.jndi.dns.timeout.initial", "3000");
        entorno.put("com.sun.jndi.dns.timeout.retries", "1");

        DirContext contexto = null;

        try {
            contexto = new InitialDirContext(entorno);
            String host = "_sebas-verification." + dominio;
            Attributes atributos = contexto.getAttributes(host, new String[]{"TXT"});

            if (atributos.get("TXT") == null) return false;

            NamingEnumeration<?> valores = atributos.get("TXT").getAll();

            while (valores.hasMore()) {
                String valor = valores.next().toString().replace("\"", "");
                if (valor.equals(token)) return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (contexto != null) {
                try { contexto.close(); } catch (Exception ignored) {}
            }
        }
    }
}
