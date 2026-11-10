package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.EnvioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Logistica manual Fase 3A. NO crea guias en Shalom/Olva ni cambia ventas. */
@RestController
@RequestMapping("/api/ventas")
public class EnvioController {
    private final EnvioService service;

    public EnvioController(EnvioService service) { this.service = service; }

    public record GuardarEnvio(
            @NotBlank @Pattern(regexp="SHALOM|OLVA|PROPIO") String operador,
            @NotBlank @Pattern(regexp="AGENCIA|DOMICILIO") String modalidad,
            @Size(max=100) String numeroGuia,
            @Size(max=100) String codigoRastreo,
            @Size(max=500) String observaciones
    ) {}

    public record CambiarEstado(
            @NotBlank @Pattern(regexp="PENDIENTE|PREPARANDO|DESPACHADO|EN_TRANSITO|DISPONIBLE_RETIRO|ENTREGADO|INCIDENCIA|CANCELADO") String estado
    ) {}

    /** Excluye ventas historicas sin tipo de venta ONLINE confirmado. */
    @GetMapping("/admin/envios/ventas-elegibles")
    public ResponseEntity<List<Integer>> ventasOnlineElegibles() {
        return ResponseEntity.ok(service.listarVentasOnlineElegibles());
    }

    @GetMapping("/admin/envios")
    public ResponseEntity<List<EnvioService.EnvioDto>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/admin/{ventaId}/envio")
    public ResponseEntity<EnvioService.EnvioDto> consultarAdmin(@PathVariable Integer ventaId) {
        return ResponseEntity.ok(service.consultarAdmin(ventaId));
    }

    @PutMapping("/admin/{ventaId}/envio")
    public ResponseEntity<EnvioService.EnvioDto> guardar(@PathVariable Integer ventaId,
                                                           @Valid @RequestBody GuardarEnvio request) {
        return ResponseEntity.ok(service.guardar(ventaId, request));
    }

    @PatchMapping("/admin/{ventaId}/envio/estado")
    public ResponseEntity<EnvioService.EnvioDto> cambiarEstado(@PathVariable Integer ventaId,
                                                                  @Valid @RequestBody CambiarEstado request) {
        return ResponseEntity.ok(service.cambiarEstado(ventaId, request.estado()));
    }

    /**
     * Vista minimizada para el cliente autenticado: nunca expone notas internas,
     * ID del registro logistico ni datos de otras compras o tiendas.
     * La pertenencia se valida en EnvioService.consultarPropio antes del mapeo.
     */
    public record SeguimientoCliente(
            Integer idVenta, String operador, String modalidad, String estado,
            String numeroGuia, String codigoRastreo, OffsetDateTime actualizadoEn) {}

    @GetMapping("/{ventaId}/envio")
    public ResponseEntity<SeguimientoCliente> consultarPropio(
            @PathVariable Integer ventaId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Acceso requerido");
        }
        EnvioService.EnvioDto envio = service.consultarPropio(ventaId, authentication.getName());
        return ResponseEntity.ok(new SeguimientoCliente(
                ventaId, envio.operador(), envio.modalidad(), envio.estado(),
                envio.numeroGuia(), envio.codigoRastreo(), envio.actualizadoEn()));
    }
}
