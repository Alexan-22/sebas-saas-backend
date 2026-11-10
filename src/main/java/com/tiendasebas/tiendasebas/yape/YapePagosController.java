package com.tiendasebas.tiendasebas.yape;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Cliente consulta solo sus pedidos; ADMIN verifica manualmente el ingreso. */
@RestController
@RequestMapping("/api/yape/pagos")
public class YapePagosController {
    private final PagoYapeManualService pagos;

    public YapePagosController(PagoYapeManualService pagos) {
        this.pagos = pagos;
    }

    @GetMapping("/mios/{idVenta}")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<PagoYapeManualService.EstadoPago> miPago(
            @PathVariable int idVenta, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pagos.consultarCliente(idVenta, auth.getName()));
    }

    @GetMapping("/mios/{idVenta}/qr")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<byte[]> miQr(@PathVariable int idVenta, Authentication auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_PNG)
                .body(pagos.qrDeCliente(idVenta, auth.getName()));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PagoYapeManualService.EstadoPago>> listar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pagos.listarAdmin());
    }

    @GetMapping("/admin/{idVenta}/auditoria")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PagoYapeManualService.EventoAuditoria>> auditoria(@PathVariable int idVenta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pagos.auditoriaAdmin(idVenta));
    }

    @PostMapping("/admin/{idVenta}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoYapeManualService.EstadoPago> aprobar(
            @PathVariable int idVenta, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pagos.decidir(idVenta, auth.getName(), true, null));
    }

    public record RechazoRequest(@NotBlank @Size(max = 250) String motivo) {}

    @PostMapping("/admin/{idVenta}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoYapeManualService.EstadoPago> rechazar(
            @PathVariable int idVenta, @Valid @RequestBody RechazoRequest req,
            Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pagos.decidir(idVenta, auth.getName(), false, req.motivo()));
    }
}
