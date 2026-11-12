
package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarContrasenaRequest(

        @NotBlank(message = "Ingresa tu contraseña actual")
        String contrasenaActual,

        @NotBlank(message = "Ingresa una contraseña nueva")
        @Size(
                min = 12,
                max = 128,
                message = "La nueva contraseña debe tener entre 12 y 128 caracteres"
        )
        String contrasenaNueva,

        @NotBlank(message = "Confirma la nueva contraseña")
        String confirmarContrasena

) {}
