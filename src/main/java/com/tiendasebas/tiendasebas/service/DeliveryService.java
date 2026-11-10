package com.tiendasebas.tiendasebas.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class DeliveryService {

    private static final BigDecimal COSTO_LIMA = new BigDecimal("10.00");
    private static final BigDecimal COSTO_PROVINCIA = new BigDecimal("20.00");

    public BigDecimal calcularCostoEnvio(String departamento, String provincia, String distrito) {
        if (departamento == null || departamento.isBlank() || provincia == null || provincia.isBlank()) {
            throw new IllegalArgumentException("Departamento y provincia son obligatorios para calcular el delivery");
        }

        String dep = departamento.trim().toUpperCase();
        String prov = provincia.trim().toUpperCase();

        if ("LIMA".equals(dep) && "LIMA".equals(prov)) {
            return COSTO_LIMA;
        }

        return COSTO_PROVINCIA;
    }
}