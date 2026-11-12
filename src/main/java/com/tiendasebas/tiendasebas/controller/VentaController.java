package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.ActualizarEstadoPedidoRequest;
import com.tiendasebas.tiendasebas.dto.CompraRequest;
import com.tiendasebas.tiendasebas.dto.CompraResponse;
import com.tiendasebas.tiendasebas.dto.DetalleCompraResponse;
import com.tiendasebas.tiendasebas.dto.DetalleVentaResponse;
import com.tiendasebas.tiendasebas.dto.VentaResponse;
import com.tiendasebas.tiendasebas.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    // =========================================================
    // CLIENTE - REALIZAR COMPRA ONLINE
    // =========================================================
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<CompraResponse> comprar(@Valid @RequestBody CompraRequest request, Authentication authentication) {
        String correo = authentication.getName();
        return ResponseEntity.ok(ventaService.realizarCompra(request, correo));
    }

    // =========================================================
    // CLIENTE - LISTAR MIS COMPRAS
    // =========================================================
    @GetMapping("/mis-compras")
    public ResponseEntity<List<VentaResponse>> misCompras(Authentication authentication) {
        String correo = authentication.getName();
        return ResponseEntity.ok(ventaService.listarMisCompras(correo));
    }

    // =========================================================
    // CLIENTE - DETALLE DE SU PROPIA COMPRA
    // =========================================================
    @GetMapping("/{idVenta}/detalle")
    public ResponseEntity<DetalleCompraResponse> detalleMiCompra(@PathVariable Integer idVenta, Authentication authentication) {
        String correo = authentication.getName();
        return ResponseEntity.ok(ventaService.obtenerDetalleMiCompra(idVenta, correo));
    }

    // =========================================================
    // ADMIN - LISTAR TODAS LAS VENTAS
    // =========================================================
    @GetMapping("/admin")
    public ResponseEntity<List<VentaResponse>> listarTodas() {
        return ResponseEntity.ok(ventaService.listarTodas());
    }

    // =========================================================
    // ADMIN - DETALLES PARA REPORTES
    // =========================================================
    @GetMapping("/admin/reportes/detalles")
    public ResponseEntity<List<DetalleVentaResponse>> listarDetallesParaReportes() {
        return ResponseEntity.ok(ventaService.listarDetallesParaReportes());
    }

    // =========================================================
    // ADMIN - DETALLE DE CUALQUIER VENTA
    // =========================================================
    @GetMapping("/admin/{idVenta}/detalle")
    public ResponseEntity<DetalleCompraResponse> detalleVentaAdmin(@PathVariable Integer idVenta) {
        return ResponseEntity.ok(ventaService.obtenerDetalleVentaAdmin(idVenta));
    }

    // =========================================================
    // ADMIN - ACTUALIZAR ESTADO LOGÍSTICO
    // =========================================================
    @PutMapping("/admin/{idVenta}/estado")
    public ResponseEntity<DetalleCompraResponse> actualizarEstadoPedido(@PathVariable Integer idVenta,
                                                                        @Valid @RequestBody ActualizarEstadoPedidoRequest request) {
        return ResponseEntity.ok(ventaService.actualizarEstadoPedido(idVenta, request.getEstadoPedido()));
    }
}