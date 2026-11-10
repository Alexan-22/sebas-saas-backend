package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.util.List;

@Data
public class VentaPosRequest {

    @NotNull(message = "Debe seleccionar un cliente presencial")
    private Integer idClientePresencial;

    @NotBlank(message = "Debe seleccionar un medio de pago")
    private String medioPago;

    @NotEmpty(message = "Debe agregar al menos un producto")
    @Valid
    private List<VentaPosItemRequest> items;
}