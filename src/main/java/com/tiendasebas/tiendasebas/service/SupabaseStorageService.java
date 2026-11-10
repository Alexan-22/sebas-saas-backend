package com.tiendasebas.tiendasebas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
public class SupabaseStorageService {

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.supabase.storage-key:}")
    private String storageKey;

    @Value("${app.supabase.service-role-key:}")
    private String serviceRole;

    @Value("${app.supabase.bucket:tienda-sebas}")
    private String bucket;

    private final RestClient client = RestClient.create();

    // =========================================================
    // VALIDAR RUTA
    // =========================================================
    private String validarRuta(String ruta) {
        if (ruta == null || ruta.isBlank()
                || ruta.startsWith("/") || ruta.contains("\\")
                || Arrays.stream(ruta.split("/", -1))
                .anyMatch(p -> p.isBlank() || p.equals(".") || p.equals(".."))
                || !ruta.matches("[a-zA-Z0-9_./-]+")) {
            throw new IllegalArgumentException("Ruta de imagen no permitida");
        }
        return ruta;
    }

    // =========================================================
    // SUBIR IMAGEN
    // =========================================================
    public String subir(MultipartFile archivo, String ruta) {
        validarRuta(ruta);

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Imagen vacía");
        }

        try {
            client.post()
                    .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + ruta)
                    .header("apikey", storageKey)
                    .header("Authorization", "Bearer " + serviceRole)
                    .contentType(MediaType.parseMediaType(archivo.getContentType()))
                    .body(archivo.getBytes())
                    .retrieve()
                    .toBodilessEntity();

            return ruta;

        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer la imagen", e);
        }
    }

    // =========================================================
    // ELIMINAR IMAGEN
    // =========================================================
    public void eliminar(String ruta) {
        validarRuta(ruta);

        client.delete()
                .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + ruta)
                .header("apikey", storageKey)
                .header("Authorization", "Bearer " + serviceRole)
                .retrieve()
                .toBodilessEntity();
    }

    // =========================================================
    // OBTENER URL PÚBLICA
    // =========================================================
    public String obtenerUrl(String ruta) {
        validarRuta(ruta);

        String rutaCodificada = Arrays.stream(ruta.split("/"))
                .map(p -> java.net.URLEncoder.encode(p, StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "/" + b)
                .orElseThrow();

        return supabaseUrl + "/storage/v1/object/public/"
                + bucket + "/" + rutaCodificada;
    }
}
