package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CompraRequest {

    private String claveIdempotencia;
    private Long cuentaBancariaId;

    @NotEmpty(message = "El carrito no puede estar vacío")
    private List<CompraItemDto> items;

    @NotBlank(message = "Debe seleccionar un medio de pago")
    private String medioPago;

    @NotNull(message = "La dirección de envío es obligatoria")
    @Valid
    private DireccionEnvioDto direccionEnvio;
}