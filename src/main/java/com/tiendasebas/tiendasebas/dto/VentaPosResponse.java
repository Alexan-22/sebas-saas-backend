package com.tiendasebas.tiendasebas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaPosResponse {

    private Integer idVenta;
    private LocalDateTime fechaVenta;
    private String tipoVenta;
    private Integer idClientePresencial;
    private String nombreCliente;
    private String apellidoCliente;
    private String tipoDocumento;
    private String numeroDocumento;
    private String telefonoCliente;
    private String correoCliente;
    private BigDecimal subtotal;
    private BigDecimal costoDelivery;
    private BigDecimal total;
    private String medioPago;
    private String estado;
    private String estadoPedido;
    private String mensaje;
}