
package com.tiendasebas.tiendasebas.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")
@RestController
public class ImagenPublicaController {

    @Value("${app.storage.provider:local}")
    private String storageProvider;

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.supabase.bucket:tienda-sebas}")
    private String bucket;

    // =========================================================
    // MOSTRAR IMÁGENES EN PRODUCCIÓN
    // =========================================================
    @GetMapping("/uploads/**")
    public ResponseEntity<Void> mostrar(
            jakarta.servlet.http.HttpServletRequest request) {

        if (!"supabase".equalsIgnoreCase(storageProvider)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String ruta = request.getRequestURI();
        String prefijo = request.getContextPath() + "/uploads/";

        if (!ruta.startsWith(prefijo)) {
            return ResponseEntity.badRequest().build();
        }

        ruta = ruta.substring(prefijo.length());

        // Aceptar únicamente las rutas de imágenes existentes.
        if (ruta.isBlank() || ruta.contains("\\")
                || Arrays.stream(ruta.split("/", -1))
                .anyMatch(p -> p.isBlank() || p.equals(".") || p.equals(".."))
                || !ruta.matches("[a-zA-Z0-9_./-]+")) {
            return ResponseEntity.badRequest().build();
        }

        // Compatibilidad con rutas antiguas.
        if (ruta.startsWith("productos/")) {
            ruta = ruta.substring("productos/".length());
        }

        String rutaCodificada = Arrays.stream(ruta.split("/"))
                .map(p -> UriUtils.encodePathSegment(p, StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "/" + b)
                .orElseThrow();

        String url = supabaseUrl + "/storage/v1/object/public/"
                + bucket + "/productos/" + rutaCodificada;

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }
}
