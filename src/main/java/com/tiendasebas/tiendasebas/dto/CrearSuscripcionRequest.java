
package com.tiendasebas.tiendasebas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CrearSuscripcionRequest(
        Long tiendaId,
        Long planId,
        LocalDate fechaInicio,
        BigDecimal precioContratado
) {}
