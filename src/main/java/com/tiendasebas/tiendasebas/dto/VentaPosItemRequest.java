package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VentaPosItemRequest {

    @NotNull(message = "El producto/talla es obligatorio")
    private Integer idProductoTalla;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor que cero")
    private Integer cantidad;
}