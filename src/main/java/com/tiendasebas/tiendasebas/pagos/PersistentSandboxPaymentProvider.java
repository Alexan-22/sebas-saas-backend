package com.tiendasebas.tiendasebas.pagos;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Adaptador interno simulado. Requiere transacciones sobre el mismo DataSource. */
public final class PersistentSandboxPaymentProvider implements PaymentProvider {
    private final JdbcTemplate jdbc;
    private final SandboxPolicy policy;
    private final TransactionTemplate transactions;

    public PersistentSandboxPaymentProvider(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this(jdbc, manager, SandboxPolicy.disabled());
    }

    public PersistentSandboxPaymentProvider(JdbcTemplate jdbc, PlatformTransactionManager manager, SandboxPolicy policy) {
        this.policy = java.util.Objects.requireNonNull(policy);
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    private void requireTenant(long tenant) {
        policy.requireAuthorized();
        if (TenantContext.getCurrentTenant() != tenant) {
            throw new NoSuchElementException("Pago no disponible");
        }
    }

    @Override
    public Pago crear(Solicitud request) {
        requireTenant(request.tiendaId());
        return transactions.execute(status -> {
            var orders = jdbc.queryForList("SELECT total, tipo_venta, medio_pago, estado FROM ventas WHERE tienda_id=? AND id_venta=? FOR UPDATE",
                    request.tiendaId(), request.pedidoId());
            if (orders.size() != 1) throw new NoSuchElementException("Pedido no disponible");
            var order = orders.getFirst();
            var existing = jdbc.queryForList("SELECT id FROM pagos_simulados WHERE tienda_id=? AND clave_idempotencia=?",
                    UUID.class, request.tiendaId(), request.claveIdempotencia());
            if (!existing.isEmpty()) {
                Pago previous = consultar(request.tiendaId(), existing.getFirst());
                if (previous.pedidoId() != request.pedidoId() || previous.monto().compareTo(request.monto()) != 0
                        || !previous.moneda().equals(request.moneda())) {
                    throw new IllegalStateException("Conflicto de idempotencia");
                }
                return previous;
            }
            if (!"ONLINE".equals(order.get("tipo_venta")) || !"SIMULADO".equals(order.get("medio_pago"))
                    || !"PENDIENTE".equals(order.get("estado"))) {
                throw new IllegalStateException("Pedido no elegible para pago simulado");
            }
            var configured = jdbc.queryForList("SELECT habilitado FROM pagos_proveedor_configuracion WHERE tienda_id=? AND proveedor='SANDBOX' FOR SHARE",
                    Boolean.class, request.tiendaId());
            if (configured.size() != 1 || !configured.getFirst()) {
                throw new IllegalStateException("Proveedor sandbox no habilitado para la tienda");
            }
            var amount = (java.math.BigDecimal) order.get("total");
            if (amount.compareTo(request.monto()) != 0 || request.monto().scale() > 2) {
                throw new IllegalArgumentException("Importe no coincide con el pedido");
            }
            UUID id = UUID.randomUUID();
            int inserted = jdbc.update("INSERT INTO pagos_simulados(id,tienda_id,id_venta,clave_idempotencia,importe,moneda,estado) VALUES (?,?,?,?,?,?,'PENDIENTE') ON CONFLICT DO NOTHING",
                    id, request.tiendaId(), request.pedidoId(), request.claveIdempotencia(), amount, request.moneda());
            var payments = jdbc.query("SELECT * FROM pagos_simulados WHERE tienda_id=? AND clave_idempotencia=?",
                    (rs, row) -> new Pago(rs.getObject("id", UUID.class), rs.getLong("tienda_id"), rs.getLong("id_venta"),
                            rs.getBigDecimal("importe"), rs.getString("moneda"), Estado.valueOf(rs.getString("estado"))),
                    request.tiendaId(), request.claveIdempotencia());
            if (payments.size() != 1 || payments.getFirst().pedidoId() != request.pedidoId()
                    || payments.getFirst().monto().compareTo(amount) != 0) {
                throw new IllegalStateException("Conflicto de idempotencia");
            }
            if (inserted == 1) jdbc.update("INSERT INTO pagos_simulados_eventos(pago_id,estado) VALUES (?,'PENDIENTE')", id);
            return payments.getFirst();
        });
    }

    @Override
    public Pago consultar(long tenant, UUID id) {
        requireTenant(tenant);
        return jdbc.query("SELECT * FROM pagos_simulados WHERE tienda_id=? AND id=?",
                (rs, row) -> new Pago(rs.getObject("id", UUID.class), rs.getLong("tienda_id"), rs.getLong("id_venta"),
                        rs.getBigDecimal("importe"), rs.getString("moneda"), Estado.valueOf(rs.getString("estado"))),
                tenant, id).stream().findFirst().orElseThrow(() -> new NoSuchElementException("Pago no disponible"));
    }

    /** Operacion interna del simulador; no publicar como aprobacion de compradores. */
    public Pago transicionar(long tenant, UUID id, Estado next, java.math.BigDecimal amount, String currency) {
        requireTenant(tenant);
        if (next == null || next == Estado.PENDIENTE || next == Estado.REEMBOLSADO) {
            throw new IllegalArgumentException("Transicion no habilitada");
        }
        return transactions.execute(status -> {
            var configured = jdbc.queryForList("SELECT habilitado FROM pagos_proveedor_configuracion WHERE tienda_id=? AND proveedor='SANDBOX' FOR SHARE", Boolean.class, tenant);
            if (configured.size() != 1 || !configured.getFirst()) throw new IllegalStateException("Proveedor sandbox no habilitado para la tienda");
            Pago reference = consultar(tenant, id);
            // Orden de locks compartido con crear: primero venta, despues pago.
            var orders = jdbc.queryForList("SELECT total,estado,medio_pago,tipo_venta FROM ventas WHERE tienda_id=? AND id_venta=? FOR UPDATE",
                    tenant, reference.pedidoId());
            if (orders.size() != 1) throw new NoSuchElementException("Pedido no disponible");
            jdbc.queryForList("SELECT id FROM pagos_simulados WHERE tienda_id=? AND id=? FOR UPDATE", tenant, id);
            Pago previous = consultar(tenant, id);
            if (amount == null || previous.monto().compareTo(amount) != 0 || !previous.moneda().equals(currency)) {
                throw new IllegalArgumentException("Importe o moneda no coinciden");
            }
            var order = orders.getFirst();
            if (!"ONLINE".equals(order.get("tipo_venta")) || !"SIMULADO".equals(order.get("medio_pago"))
                    || previous.monto().compareTo((java.math.BigDecimal) order.get("total")) != 0) {
                throw new IllegalStateException("Pedido incompatible con el pago");
            }
            if (previous.estado() == next) return previous;
            if (previous.estado() != Estado.PENDIENTE || !"PENDIENTE".equals(order.get("estado"))) {
                throw new IllegalStateException("Transicion no permitida");
            }
            if (next == Estado.APROBADO) {
                int updated = jdbc.update("UPDATE ventas SET estado='PAGADA' WHERE tienda_id=? AND id_venta=? AND estado='PENDIENTE'",
                        tenant, previous.pedidoId());
                if (updated != 1) throw new IllegalStateException("Pedido cambio de estado");
            }
            jdbc.update("UPDATE pagos_simulados SET estado=?,actualizado_en=now() WHERE tienda_id=? AND id=?", next.name(), tenant, id);
            jdbc.update("INSERT INTO pagos_simulados_eventos(pago_id,estado) VALUES (?,?)", id, next.name());
            return consultar(tenant, id);
        });
    }
}
