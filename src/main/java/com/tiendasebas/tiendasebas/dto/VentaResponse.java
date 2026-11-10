package com.tiendasebas.tiendasebas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaResponse {

    private Integer idVenta;
    private String tipoVenta;
    private Integer idUsuario;
    private Integer idClientePresencial;
    private String nombreCliente;
    private String apellidoCliente;
    private String correoCliente;
    private String tipoDocumento;
    private String numeroDocumento;
    private LocalDateTime fechaVenta;
    private BigDecimal total;
    private String estado;
    private String estadoPedido;
    private String medioPago;
}