package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.ActualizarEstadoPedidoRequest;
import com.tiendasebas.tiendasebas.dto.DetalleCompraResponse;
import com.tiendasebas.tiendasebas.service.VentaService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/rbac/empleado/pedidos")
public class RbacPedidosEmpleadoController {

    private static final Set<String> ESTADOS_PERMITIDOS = Set.of(
            "EN_PREPARACION",
            "ENVIADO",
            "ENTREGADO"
    );

    private final VentaService ventaService;

    public RbacPedidosEmpleadoController(
            VentaService ventaService
    ) {
        this.ventaService = ventaService;
    }

    @PutMapping("/{idVenta}/estado")
    @PreAuthorize(
        "hasRole('EMPLEADO') and " +
        "@rbacAutorizacionService.tienePermiso(authentication, 'pedidos.gestionar')"
    )
    public DetalleCompraResponse actualizarEstado(
            @PathVariable Integer idVenta,
            @Valid @RequestBody ActualizarEstadoPedidoRequest request
    ) {
        if (idVenta == null || idVenta <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identificador de pedido invalido"
            );
        }

        if (request == null
                || request.getEstadoPedido() == null
                || request.getEstadoPedido().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estado del pedido es obligatorio"
            );
        }

        String estado = request.getEstadoPedido()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!ESTADOS_PERMITIDOS.contains(estado)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Estado no permitido para empleados"
            );
        }

        // VentaService vuelve a validar:
        // - Tienda del pedido
        // - Transicion logistica
        // - Estado comercial COD
        // - Aprobacion previa Yape/transferencia
        // - Bloqueo y transaccion de la venta

        return ventaService.actualizarEstadoPedido(
                idVenta,
                estado
        );
    }
}