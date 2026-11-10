package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientePresencialRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String apellido;

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Size(max = 20, message = "El tipo de documento no puede superar los 20 caracteres")
    private String tipoDocumento;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 20, message = "El número de documento no puede superar los 20 caracteres")
    private String numeroDocumento;

    @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
    private String telefono;

    @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
    private String correo;
}