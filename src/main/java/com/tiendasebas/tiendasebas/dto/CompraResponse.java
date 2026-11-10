package com.tiendasebas.tiendasebas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class CompraResponse {

    private Integer idVenta;
    private BigDecimal subtotal;
    private BigDecimal costoDelivery;
    private BigDecimal total;
    private String estado;
    private String medioPago;
    private String mensaje;
}