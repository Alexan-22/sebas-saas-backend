package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.security.AuthRateLimit;
import com.tiendasebas.tiendasebas.service.EntregaPrivadaReclamacionesService;
import com.tiendasebas.tiendasebas.service.LibroReclamacionesService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Endpoint separado: no modifica el antiguo /publico, que continua denegado. */
@RestController
@RequestMapping("/api/v1/reclamaciones")
public class EntregaPrivadaReclamacionesController {
    private final EntregaPrivadaReclamacionesService servicio;
    private final AuthRateLimit rateLimit;
    private final boolean habilitado;

    public EntregaPrivadaReclamacionesController(EntregaPrivadaReclamacionesService servicio,
                                                AuthRateLimit rateLimit,
                                                @Value("${app.reclamaciones.entrega-habilitada:false}") boolean habilitado) {
        this.servicio = servicio;
        this.rateLimit = rateLimit;
        this.habilitado = habilitado;
    }

    public record ConsultaToken(String token) {}

    @PostMapping("/registro")
    public ResponseEntity<EntregaPrivadaReclamacionesService.Confirmacion> registrar(
            @RequestBody LibroReclamacionesService.Solicitud solicitud, HttpServletRequest request) {
        verificar(request);
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(servicio.registrar(solicitud));
    }

    @PostMapping(value = "/constancia", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> constancia(@RequestBody ConsultaToken consulta, HttpServletRequest request) {
        verificar(request);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header("Referrer-Policy", "no-referrer")
                .body(servicio.html(consulta == null ? null : consulta.token()));
    }

    private void verificar(HttpServletRequest request) {
        if (!habilitado) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Libro no disponible");
        long tenant = TenantContext.getCurrentTenant();
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Origen no disponible");
        rateLimit.comprobar("reclamaciones-entrega:" + tenant, ip, 5);
    }
}
