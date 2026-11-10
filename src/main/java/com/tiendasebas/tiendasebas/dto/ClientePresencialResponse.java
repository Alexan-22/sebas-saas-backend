package com.tiendasebas.tiendasebas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientePresencialResponse {

    private Integer idClientePresencial;
    private String nombre;
    private String apellido;
    private String tipoDocumento;
    private String numeroDocumento;
    private String telefono;
    private String correo;
    private LocalDateTime fechaRegistro;
    private Boolean estado;
}