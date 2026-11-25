package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.ClienteInvitadoDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutInvitadoClienteService {

    private final JdbcTemplate jdbc;

    public CheckoutInvitadoClienteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Integer idVenta, ClienteInvitadoDto cliente) {

        Long tiendaId = TenantContext.getCurrentTenant();

        if (tiendaId == null || tiendaId <= 0) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Tienda no identificada"
            );
        }

        if (idVenta == null || idVenta <= 0 || cliente == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Datos del comprador invalidos"
            );
        }

        int filas = jdbc.update("""
            INSERT INTO public.venta_clientes_invitados
                (tienda_id, id_venta, nombres, apellidos, correo, telefono)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            tiendaId,
            idVenta,
            cliente.getNombres().trim(),
            cliente.getApellidos().trim(),
            cliente.getCorreo().trim().toLowerCase(java.util.Locale.ROOT),
            cliente.getTelefono().trim()
        );

        if (filas != 1) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No se pudo registrar al comprador"
            );
        }
    }
}