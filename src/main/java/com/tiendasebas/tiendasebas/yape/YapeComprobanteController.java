package com.tiendasebas.tiendasebas.yape;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Solo CLIENTE propietario y ADMIN del tenant autenticado. */
@RestController
@RequestMapping("/api/yape/pagos")
public class YapeComprobanteController {
    private final YapeComprobanteService service;

    public YapeComprobanteController(YapeComprobanteService service) {
        this.service = service;
    }

    @PostMapping(value = "/mios/{idVenta}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<YapeComprobanteService.ComprobanteInfo> subir(
            @PathVariable int idVenta, @RequestParam("archivo") MultipartFile archivo,
            Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.subir(idVenta, auth.getName(), archivo));
    }

    @GetMapping("/mios/{idVenta}/comprobante")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<YapeComprobanteService.ComprobanteInfo> infoCliente(
            @PathVariable int idVenta, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.infoCliente(idVenta, auth.getName()));
    }

    @GetMapping("/mios/{idVenta}/comprobante/imagen")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<byte[]> imagenCliente(@PathVariable int idVenta, Authentication auth) {
        return responderImagen(service.imagenCliente(idVenta, auth.getName()));
    }

    @GetMapping("/mios/{idVenta}/contacto")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<YapeComprobanteService.ContactoWhatsapp> contacto(
            @PathVariable int idVenta, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.contacto(idVenta, auth.getName()));
    }

    @GetMapping("/admin/{idVenta}/comprobante")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<YapeComprobanteService.ComprobanteInfo> infoAdmin(@PathVariable int idVenta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.infoAdmin(idVenta));
    }

    @GetMapping("/admin/{idVenta}/comprobante/imagen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> imagenAdmin(@PathVariable int idVenta) {
        return responderImagen(service.imagenAdmin(idVenta));
    }

    private static ResponseEntity<byte[]> responderImagen(YapeComprobanteService.Imagen imagen) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .contentType(MediaType.parseMediaType(imagen.mimeTipo()))
                .body(imagen.bytes());
    }
}
