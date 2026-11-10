package com.tiendasebas.tiendasebas.pagos;

import com.tiendasebas.tiendasebas.config.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.UUID;

/** Administracion sandbox opt-in. No expone aprobaciones ni credenciales. */
@RestController
@Profile("payments-sandbox & !prod & !production")
@RequestMapping("/api/pagos/admin")
@PreAuthorize("hasRole('ADMIN')")
public class PaymentAdminController {
    private final JdbcTemplate jdbc;
    private final SandboxPolicy policy;
    public PaymentAdminController(JdbcTemplate jdbc, org.springframework.core.env.Environment environment) { this.jdbc = jdbc; this.policy = new SandboxPolicy(environment); }
    public record Configuracion(String proveedor, boolean habilitado, String modo) {}
    public record ActualizarConfiguracion(@NotNull Boolean habilitado) {}

    @GetMapping("/configuracion")
    public Configuracion configuracion() {
        policy.requireAuthorized();
        var values = jdbc.queryForList("SELECT habilitado FROM pagos_proveedor_configuracion WHERE tienda_id=? AND proveedor='SANDBOX'",
                Boolean.class, TenantContext.getCurrentTenant());
        return new Configuracion("SANDBOX", !values.isEmpty() && values.getFirst(), "SANDBOX");
    }

    @PutMapping("/configuracion")
    public Configuracion configurar(@Valid @RequestBody ActualizarConfiguracion request) {
        policy.requireAuthorized();
        jdbc.update("INSERT INTO pagos_proveedor_configuracion(tienda_id,proveedor,habilitado) VALUES (?,'SANDBOX',?) ON CONFLICT(tienda_id,proveedor) DO UPDATE SET habilitado=excluded.habilitado,actualizado_en=now()",
                TenantContext.getCurrentTenant(), request.habilitado());
        return new Configuracion("SANDBOX", request.habilitado(), "SANDBOX");
    }

    @GetMapping
    public List<PaymentProvider.Pago> listar(@RequestParam(defaultValue="0") int pagina) {
        policy.requireAuthorized();
        if (pagina < 0 || pagina > 1000000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return jdbc.query("SELECT * FROM pagos_simulados WHERE tienda_id=? ORDER BY creado_en DESC,id LIMIT 50 OFFSET ?",
                this::pago, TenantContext.getCurrentTenant(), pagina * 50);
    }

    @GetMapping("/{id}")
    public PaymentProvider.Pago consultar(@PathVariable UUID id) {
        policy.requireAuthorized();
        return jdbc.query("SELECT * FROM pagos_simulados WHERE tienda_id=? AND id=?", this::pago,
                TenantContext.getCurrentTenant(), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private PaymentProvider.Pago pago(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PaymentProvider.Pago(rs.getObject("id", UUID.class), rs.getLong("tienda_id"),
                rs.getLong("id_venta"), rs.getBigDecimal("importe"), rs.getString("moneda"),
                PaymentProvider.Estado.valueOf(rs.getString("estado")));
    }
}
