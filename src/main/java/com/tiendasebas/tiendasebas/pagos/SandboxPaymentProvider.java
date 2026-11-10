package com.tiendasebas.tiendasebas.pagos;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** SIMULADOR SOLO PARA PRUEBAS; no usar como proveedor de produccion. */
public final class SandboxPaymentProvider implements PaymentProvider {
    private final SandboxPolicy policy;
    public SandboxPaymentProvider() { this(SandboxPolicy.disabled()); }
    public SandboxPaymentProvider(SandboxPolicy policy) { this.policy = java.util.Objects.requireNonNull(policy); }
    private void authorize(long tenant) {
        policy.requireAuthorized();
        if (com.tiendasebas.tiendasebas.config.TenantContext.getCurrentTenant() != tenant) throw new NoSuchElementException("Pago no disponible");
    }
    private final Map<String, UUID> claves = new ConcurrentHashMap<>();
    private final Map<UUID, Pago> pagos = new ConcurrentHashMap<>();

    @Override
    public synchronized Pago crear(Solicitud solicitud) {
        Objects.requireNonNull(solicitud, "solicitud");
        authorize(solicitud.tiendaId());
        String clave = solicitud.tiendaId() + ":" + solicitud.claveIdempotencia();
        UUID existente = claves.get(clave);
        if (existente != null) {
            Pago pago = pagos.get(existente);
            if (pago.pedidoId() != solicitud.pedidoId() ||
                pago.monto().compareTo(solicitud.monto()) != 0 ||
                !pago.moneda().equals(solicitud.moneda())) {
                throw new IllegalStateException("Conflicto de idempotencia");
            }
            return pago;
        }
        Pago nuevo = new Pago(UUID.randomUUID(), solicitud.tiendaId(), solicitud.pedidoId(),
                              solicitud.monto(), solicitud.moneda(), Estado.PENDIENTE);
        pagos.put(nuevo.id(), nuevo);
        claves.put(clave, nuevo.id());
        return nuevo;
    }

    @Override
    public Pago consultar(long tiendaId, UUID pagoId) {
        authorize(tiendaId);
        Pago pago = pagos.get(pagoId);
        if (pago == null || pago.tiendaId() != tiendaId) {
            throw new NoSuchElementException("Pago inexistente para esta tienda");
        }
        return pago;
    }

    /** Simula confirmacion SOLO en tests Java; no existe endpoint HTTP. */
    public synchronized Pago aprobarSimulacion(long tiendaId, UUID pagoId) {
        Pago previo = consultar(tiendaId, pagoId);
        if (previo.estado() == Estado.APROBADO) return previo;
        Pago aprobado = new Pago(previo.id(), previo.tiendaId(), previo.pedidoId(),
                                 previo.monto(), previo.moneda(), Estado.APROBADO);
        pagos.put(pagoId, aprobado);
        return aprobado;
    }
}
