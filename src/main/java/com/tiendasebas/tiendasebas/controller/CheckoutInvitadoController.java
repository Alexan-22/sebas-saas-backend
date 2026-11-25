package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.CompraInvitadoRequest;
import com.tiendasebas.tiendasebas.dto.CompraInvitadoResponse;
import com.tiendasebas.tiendasebas.dto.DetalleCompraResponse;
import com.tiendasebas.tiendasebas.service.CheckoutInvitadoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ventas/invitado")
public class CheckoutInvitadoController {
    private final CheckoutInvitadoService checkout;
    public CheckoutInvitadoController(CheckoutInvitadoService checkout) { this.checkout = checkout; }

    @PostMapping
    public ResponseEntity<CompraInvitadoResponse> comprar(@Valid @RequestBody CompraInvitadoRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(checkout.comprar(request));
    }

    @GetMapping("/{idVenta}")
    public ResponseEntity<DetalleCompraResponse> detalle(@PathVariable int idVenta,
                                                          @RequestHeader("X-Checkout-Token") String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(checkout.detalle(idVenta, token));
    }
}