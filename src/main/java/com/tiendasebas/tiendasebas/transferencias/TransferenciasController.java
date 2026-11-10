package com.tiendasebas.tiendasebas.transferencias;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/transferencias")
public class TransferenciasController {
    private final TransferenciaManualService service;
    public TransferenciasController(TransferenciaManualService service) {this.service=service;}

    @GetMapping("/publico")
    public ResponseEntity<List<TransferenciaManualService.Cuenta>> publicas(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.cuentasPublicas());
    }
    @GetMapping("/admin/cuentas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransferenciaManualService.Cuenta>> cuentasAdmin(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.cuentasAdmin());
    }
    @PutMapping("/admin/cuentas/{banco}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransferenciaManualService.Cuenta> guardarCuenta(
            @PathVariable String banco,@RequestBody TransferenciaManualService.Cuenta datos){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.guardarCuenta(banco,datos));
    }

    @GetMapping("/pagos/mios/{idVenta}")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<TransferenciaManualService.Pago> mio(@PathVariable int idVenta,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultarCliente(idVenta,auth.getName()));
    }
    @GetMapping("/pagos/mios/{idVenta}/contacto")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<TransferenciaManualService.Whatsapp> contacto(@PathVariable int idVenta,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.whatsapp(idVenta,auth.getName()));
    }
    @PostMapping(path="/pagos/mios/{idVenta}/comprobante",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<TransferenciaManualService.ComprobanteInfo> subir(
            @PathVariable int idVenta,Authentication auth,@RequestParam("archivo") MultipartFile archivo){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.subirComprobante(idVenta,auth.getName(),archivo));
    }
    @PostMapping(path="/pagos/mios/{idVenta}/comprobante/reemplazo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<TransferenciaManualService.ComprobanteInfo> reemplazar(@PathVariable int idVenta,Authentication auth,
            @RequestParam("archivo") MultipartFile archivo,@RequestParam("motivo") String motivo){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reemplazarComprobante(idVenta,auth.getName(),archivo,motivo));
    }
    @GetMapping("/pagos/mios/{idVenta}/comprobante")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<TransferenciaManualService.ComprobanteInfo> infoMio(@PathVariable int idVenta,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.infoCliente(idVenta,auth.getName()));
    }
    @GetMapping("/pagos/mios/{idVenta}/comprobante/imagen")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<byte[]> imagenMia(@PathVariable int idVenta,Authentication auth){
        return imagen(service.imagenCliente(idVenta,auth.getName()));
    }
    @GetMapping("/pagos/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransferenciaManualService.Pago>> listar(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listarAdmin());
    }
    @GetMapping("/pagos/admin/{idVenta}/auditoria")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransferenciaManualService.Evento>> auditoria(@PathVariable int idVenta){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.auditoriaAdmin(idVenta));
    }
    @GetMapping("/pagos/admin/{idVenta}/comprobante")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransferenciaManualService.ComprobanteInfo> infoAdmin(@PathVariable int idVenta){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.infoAdmin(idVenta));
    }
    @GetMapping("/pagos/admin/{idVenta}/comprobante/imagen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> imagenAdmin(@PathVariable int idVenta){return imagen(service.imagenAdmin(idVenta));}
    private ResponseEntity<byte[]> imagen(TransferenciaManualService.Imagen img){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"inline")
            .header("X-Content-Type-Options","nosniff")
            .contentType(MediaType.parseMediaType(img.mime())).body(img.bytes());
    }
    @PostMapping("/pagos/admin/{idVenta}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransferenciaManualService.Pago> aprobar(@PathVariable int idVenta,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.decidir(idVenta,auth.getName(),true,null));
    }
    public record Rechazo(@NotBlank @Size(max=250) String motivo){}
    @PostMapping("/pagos/admin/{idVenta}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransferenciaManualService.Pago> rechazar(
            @PathVariable int idVenta,@Valid @RequestBody Rechazo req,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.decidir(idVenta,auth.getName(),false,req.motivo()));
    }
}
