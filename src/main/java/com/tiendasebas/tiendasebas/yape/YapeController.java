package com.tiendasebas.tiendasebas.yape;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/yape")
public class YapeController {

    private final YapeConfiguracionService service;

    public YapeController(YapeConfiguracionService service) {
        this.service = service;
    }

    // Acceso público únicamente si SecurityConfig permite esta ruta exacta.
    // El service además exige tienda ACTIVA + configuración habilitada.
    @GetMapping("/publico")
    public ResponseEntity<YapeConfiguracion> publico() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.consultarPublico());
    }

    @GetMapping("/qr")
    public ResponseEntity<byte[]> qrPublico() {
        return imagen(service.imagenPublica());
    }

    @GetMapping("/admin/configuracion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<YapeConfiguracion> configuracion() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.consultarAdmin());
    }

    @PutMapping("/admin/configuracion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<YapeConfiguracion> actualizar(
            @Valid @RequestBody ActualizarYapeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.actualizar(request));
    }

    @PostMapping(value = "/admin/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<YapeConfiguracion> subirQr(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > YapeQrValidator.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El QR debe ser una imagen de hasta 2 MB");
        }
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(service.cargarQr(archivo.getBytes()));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen QR");
        }
    }

    @GetMapping("/admin/qr")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> qrAdmin() {
        return imagen(service.imagenAdmin());
    }

    private static ResponseEntity<byte[]> imagen(YapeConfiguracionService.ImagenQr imagen) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_PNG)
                .body(imagen.datos());
    }
}
