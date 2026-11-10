package com.tiendasebas.tiendasebas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DireccionEnvioResponse {

    private Long id;
    private String departamento;
    private String provincia;
    private String distrito;
    private String direccionExacta;
    private String referencia;
    private BigDecimal costoDelivery;
}