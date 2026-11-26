package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClienteInvitadoDto {

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 100)
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100)
    private String apellidos;

    @NotBlank(message = "El correo es obligatorio")
    @Email
    @Size(max = 150)
    private String correo;

    @NotBlank(message = "El telefono es obligatorio")
    @Pattern(
        regexp = "^9[0-9]{8}$",
        message = "Ingresa un celular peruano valido"
    )
    private String telefono;
}