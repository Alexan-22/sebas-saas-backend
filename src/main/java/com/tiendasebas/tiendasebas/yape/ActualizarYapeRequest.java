package com.tiendasebas.tiendasebas.yape;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActualizarYapeRequest(
        @NotNull Boolean habilitado,
        @Size(max = 9) String numeroYape,
        @Size(max = 120) String titularYape,
        @Size(max = 1000) String qrUrl
) {}
