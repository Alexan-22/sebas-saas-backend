package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.dto.ClientePresencialRequest;
import com.tiendasebas.tiendasebas.dto.ClientePresencialResponse;
import com.tiendasebas.tiendasebas.service.ClientePresencialService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/clientes-presenciales/admin")
public class ClientePresencialController {

    private final ClientePresencialService clientePresencialService;

    public ClientePresencialController(ClientePresencialService clientePresencialService) {
        this.clientePresencialService = clientePresencialService;
    }

    // ADMIN - LISTAR CLIENTES PRESENCIALES
    @GetMapping
    public ResponseEntity<List<ClientePresencialResponse>> listar() {
        return ResponseEntity.ok(clientePresencialService.listarActivos());
    }

    // ADMIN - OBTENER CLIENTE PRESENCIAL
    @GetMapping("/{idClientePresencial}")
    public ResponseEntity<ClientePresencialResponse> obtener(@PathVariable Integer idClientePresencial) {
        return ResponseEntity.ok(clientePresencialService.obtenerPorId(idClientePresencial));
    }

    // ADMIN - REGISTRAR CLIENTE PRESENCIAL
    @PutMapping("/{idClientePresencial}")
    public ResponseEntity<ClientePresencialResponse> actualizar(
            @PathVariable Integer idClientePresencial,
            @Valid @RequestBody ClientePresencialRequest request) {
        return ResponseEntity.ok(clientePresencialService.actualizar(idClientePresencial, request));
    }

    // ADMIN - REGISTRAR CLIENTE PRESENCIAL
    @PostMapping
    public ResponseEntity<ClientePresencialResponse> registrar(@Valid @RequestBody ClientePresencialRequest request) {
        return ResponseEntity.ok(clientePresencialService.registrar(request));
    }
}
