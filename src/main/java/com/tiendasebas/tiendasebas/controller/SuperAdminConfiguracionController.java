
package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.SaasActividadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/superadmin/configuracion")
@RequiredArgsConstructor
public class SuperAdminConfiguracionController {

    private final JdbcTemplate jdbc;
    private final SaasActividadService actividad;

    private static final Set<String> CLAVES = Set.of(
            "nombre_plataforma",
            "correo_soporte",
            "telefono_soporte",
            "zona_horaria",
            "dias_prueba",
            "dias_alerta_vencimiento"
    );

    @GetMapping
    public Map<String, String> obtener() {
        Map<String, String> configuracion = new LinkedHashMap<>();

        jdbc.query("""
            SELECT clave, valor
            FROM public.saas_configuracion
            ORDER BY clave
            """, rs -> {
            String clave = rs.getString("clave");
            if (CLAVES.contains(clave)) {
                configuracion.put(clave, rs.getString("valor"));
            }
        });

        return configuracion;
    }

    @PutMapping
    @Transactional
    public Map<String, String> guardar(@RequestBody Map<String, String> datos) {
        if (datos == null || !datos.keySet().equals(CLAVES)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes enviar las seis opciones de configuración.");
        }

        Map<String, String> actuales = obtener();

        for (String clave : CLAVES) {
            String valor = validar(clave, datos.get(clave));

            jdbc.update("""
                INSERT INTO public.saas_configuracion
                    (clave, valor, actualizado_en)
                VALUES (?, ?, NOW())
                ON CONFLICT (clave) DO UPDATE
                SET valor = EXCLUDED.valor,
                    actualizado_en = NOW()
                """, clave, valor);

            if (!valor.equals(actuales.get(clave))) {
                actividad.registrar(
                        "ACTUALIZAR",
                        "CONFIGURACION",
                        clave,
                        "Se actualizó el parámetro " + clave
                );
            }
        }

        return obtener();
    }

    private String validar(String clave, String valor) {
        if (valor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Falta el campo: " + clave);
        }

        String texto = valor.trim();

        switch (clave) {
            case "nombre_plataforma" -> {
                if (texto.isEmpty() || texto.length() > 100) {
                    throw error(clave);
                }
            }
            case "correo_soporte" -> {
                if (texto.length() > 150 ||
                        (!texto.isEmpty() &&
                                !texto.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                    throw error(clave);
                }
            }
            case "telefono_soporte" -> {
                if (texto.length() > 25 ||
                        !texto.matches("^[+0-9() .-]*$")) {
                    throw error(clave);
                }
            }
            case "zona_horaria" -> {
                try {
                    java.time.ZoneId.of(texto);
                } catch (Exception e) {
                    throw error(clave);
                }
            }
            case "dias_prueba", "dias_alerta_vencimiento" -> {
                try {
                    int dias = Integer.parseInt(texto);
                    if (dias < 0 || dias > 365) throw error(clave);
                } catch (NumberFormatException e) {
                    throw error(clave);
                }
            }
            default -> throw error(clave);
        }

        return texto;
    }

    private ResponseStatusException error(String clave) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Valor inválido para " + clave);
    }
}
