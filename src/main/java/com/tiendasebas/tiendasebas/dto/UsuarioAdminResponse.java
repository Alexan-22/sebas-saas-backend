package com.tiendasebas.tiendasebas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UsuarioAdminResponse {

    private Integer idUsuario;
    private String nombre;
    private String apellido;
    private String correo;
    private String rol;
    private Boolean estado;
}