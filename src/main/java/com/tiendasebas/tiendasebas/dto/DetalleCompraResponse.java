package com.tiendasebas.tiendasebas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCompraResponse {

    private Integer idVenta;
    private LocalDateTime fechaVenta;
    private String tipoVenta;
    private BigDecimal subtotal;
    private BigDecimal costoDelivery;
    private BigDecimal total;
    private String estado;
    private String estadoPedido;
    private String medioPago;
    private Integer idUsuario;
    private Integer idClientePresencial;
    private String nombreCliente;
    private String apellidoCliente;
    private String correoCliente;
    private String tipoDocumento;
    private String numeroDocumento;
    private String telefonoCliente;
    private DireccionEnvioResponse direccionEnvio;
    private List<DetalleVentaResponse> detalles;
}