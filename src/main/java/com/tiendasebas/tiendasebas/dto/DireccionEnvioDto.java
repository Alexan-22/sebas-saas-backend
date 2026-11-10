package com.tiendasebas.tiendasebas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DireccionEnvioDto {

    @NotBlank(message = "El departamento es obligatorio")
    @Size(max = 100, message = "El departamento no puede superar los 100 caracteres")
    private String departamento;

    @NotBlank(message = "La provincia es obligatoria")
    @Size(max = 100, message = "La provincia no puede superar los 100 caracteres")
    private String provincia;

    @NotBlank(message = "El distrito es obligatorio")
    @Size(max = 100, message = "El distrito no puede superar los 100 caracteres")
    private String distrito;

    @NotBlank(message = "La dirección exacta es obligatoria")
    @Size(max = 255, message = "La dirección exacta no puede superar los 255 caracteres")
    private String direccionExacta;

    @Size(max = 255, message = "La referencia no puede superar los 255 caracteres")
    private String referencia;
}