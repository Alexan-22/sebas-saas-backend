package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActualizarEstadoPedidoRequest {

    @NotBlank(message = "El estado del pedido es obligatorio")
    private String estadoPedido;
}