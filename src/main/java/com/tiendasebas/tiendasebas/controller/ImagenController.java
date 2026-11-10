package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.SupabaseStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    private final SupabaseStorageService supabaseStorageService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.storage.provider:local}")
    private String storageProvider;

    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    private static final Map<String, Set<String>> TIPOS_PERMITIDOS = Map.of(
            "jpg", Set.of("image/jpeg", "image/jpg"),
            "jpeg", Set.of("image/jpeg", "image/jpg"),
            "jfif", Set.of("image/jpeg", "image/jpg"),
            "png", Set.of("image/png"),
            "webp", Set.of("image/webp")
    );

    public ImagenController(SupabaseStorageService supabaseStorageService) {
        this.supabaseStorageService = supabaseStorageService;
    }

    // SUBIR IMAGEN PRINCIPAL DEL PRODUCTO
    @PostMapping(value = "/producto", consumes = "multipart/form-data")
    public ResponseEntity<String> subirImagen(
            @RequestParam("imagen") MultipartFile imagen) throws IOException {

        if (imagen == null || imagen.isEmpty()) {
            return ResponseEntity.badRequest().body("No se recibió ninguna imagen");
        }

        if (imagen.getSize() > TAMANO_MAXIMO) {
            return ResponseEntity.badRequest().body("La imagen no puede superar los 5 MB");
        }

        String nombreOriginal = imagen.getOriginalFilename();
        int punto = nombreOriginal != null ? nombreOriginal.lastIndexOf('.') : -1;

        if (punto < 0 || punto == nombreOriginal.length() - 1) {
            return ResponseEntity.badRequest().body("El archivo no tiene una extensión válida");
        }

        String extension = nombreOriginal.substring(punto + 1).toLowerCase(Locale.ROOT);
        String tipo = imagen.getContentType();
        tipo = tipo != null ? tipo.toLowerCase(Locale.ROOT).trim() : "";

        Set<String> tiposAceptados = TIPOS_PERMITIDOS.get(extension);
        if (tiposAceptados == null || !tiposAceptados.contains(tipo)) {
            return ResponseEntity.badRequest()
                    .body("Solo se permiten imágenes JPG, JPEG, JFIF, PNG o WEBP");
        }

        String nombreArchivo = UUID.randomUUID() + "." + extension;

        if ("supabase".equalsIgnoreCase(storageProvider)) {
            // Producción: guardar en el bucket de Supabase.
            supabaseStorageService.subir(imagen, "productos/" + nombreArchivo);

        } else if ("local".equalsIgnoreCase(storageProvider)) {
            // Desarrollo: guardar en la carpeta configurada.
            Path directorio = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(directorio);

            Path destino = directorio.resolve(nombreArchivo).normalize();
            if (!destino.startsWith(directorio)) {
                throw new IllegalArgumentException("Ruta de archivo no permitida");
            }

            try (InputStream contenido = imagen.getInputStream()) {
                Files.copy(contenido, destino);
            }

        } else {
            throw new IllegalStateException("Proveedor de almacenamiento no válido");
        }

        return ResponseEntity.ok(nombreArchivo);
    }
}
