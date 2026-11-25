package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.dto.CompraInvitadoRequest;
import com.tiendasebas.tiendasebas.dto.CompraInvitadoResponse;
import com.tiendasebas.tiendasebas.dto.DetalleCompraResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutInvitadoService {
    private final VentaService ventas;
    private final CheckoutInvitadoClienteService clientes;
    private final CheckoutInvitadoIdempotenciaService idempotencia;
    private final CheckoutInvitadoAccesoService accesos;

    public CheckoutInvitadoService(VentaService ventas, CheckoutInvitadoClienteService clientes,
                                  CheckoutInvitadoIdempotenciaService idempotencia,
                                  CheckoutInvitadoAccesoService accesos) {
        this.ventas = ventas;
        this.clientes = clientes;
        this.idempotencia = idempotencia;
        this.accesos = accesos;
    }

    @Transactional
    public CompraInvitadoResponse comprar(CompraInvitadoRequest solicitud) {
        Integer anterior = idempotencia.recuperar(solicitud);
        if (anterior != null) {
            // La clave UUID no acredita titularidad. Nunca devolver una credencial en un reintento.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pedido ya registrado. Usa el enlace privado entregado en la primera respuesta");
        }
        var compra = ventas.realizarCompraInvitadoInterna(solicitud);
        clientes.registrar(compra.getIdVenta(), solicitud.getCliente());
        idempotencia.recordar(solicitud, compra.getIdVenta());
        String token = accesos.generarAcceso(compra.getIdVenta());
        return new CompraInvitadoResponse(compra, token);
    }

    @Transactional(readOnly = true)
    public DetalleCompraResponse detalle(int idVenta, String token) {
        if (!accesos.accesoValido(idVenta, token)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        return ventas.obtenerDetalleVentaAdmin(idVenta);
    }
}