package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.security.AuthRateLimit;
import com.tiendasebas.tiendasebas.service.LibroReclamacionesService;
import com.tiendasebas.tiendasebas.service.LibroReclamacionesService.Solicitud;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoint preparado para pruebas, INHABILITADO por defecto.
 * No habilitar hasta tener constancia, entrega, pruebas HTTP/RLS y política de datos.
 * SecurityConfig conserva su comportamiento actual: NO agregar permitAll aún.
 */
@RestController
@RequestMapping("/api/v1/reclamaciones")
public class LibroReclamacionesController {
    private final LibroReclamacionesService servicio;
    private final AuthRateLimit rateLimit;
    private final boolean habilitado;

    public LibroReclamacionesController(
            LibroReclamacionesService servicio,
            AuthRateLimit rateLimit,
            @Value("${app.reclamaciones.registro-publico-habilitado:false}") boolean habilitado) {
        this.servicio = servicio;
        this.rateLimit = rateLimit;
        this.habilitado = habilitado;
    }

    /** Respuesta mínima: no revela el ID interno global ni datos personales. */
    public record Confirmacion(long numeroHoja) {}

    @PostMapping("/publico")
    public ResponseEntity<Confirmacion> registrar(
            @RequestBody Solicitud solicitud,
            HttpServletRequest request) {
        if (!habilitado) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no disponible");
        }
        long tiendaId = TenantContext.getCurrentTenant();
        // El encabezado X-Forwarded-For es controlable por clientes si el proxy
        // no está configurado para sanearlo; usar únicamente remoteAddr aquí.
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Origen no disponible");
        }
        // AuthRateLimit persiste límites en ventanas de 10 minutos.
        rateLimit.comprobar("reclamaciones:" + tiendaId, ip, 5);
        var registro = servicio.registrar(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .body(new Confirmacion(registro.numeroHoja()));
    }
}
