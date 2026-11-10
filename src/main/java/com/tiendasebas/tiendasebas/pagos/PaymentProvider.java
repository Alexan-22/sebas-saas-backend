package com.tiendasebas.tiendasebas.pagos;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Interfaz neutral. Sin claves ni acceso a pasarelas reales. */
public interface PaymentProvider {
    enum Estado { PENDIENTE, APROBADO, RECHAZADO, EXPIRADO, REEMBOLSADO }

    record Solicitud(long tiendaId, long pedidoId, BigDecimal monto, String moneda,
                     String claveIdempotencia) {
        public Solicitud {
            if (tiendaId <= 0 || pedidoId <= 0) throw new IllegalArgumentException("IDs invalidos");
            Objects.requireNonNull(monto, "monto");
            if (monto.signum() <= 0) throw new IllegalArgumentException("Monto invalido");
            if (!"PEN".equals(moneda)) throw new IllegalArgumentException("Se requiere PEN");
            if (claveIdempotencia == null || claveIdempotencia.isBlank())
                throw new IllegalArgumentException("Falta idempotencia");
        }
    }

    record Pago(UUID id, long tiendaId, long pedidoId, BigDecimal monto,
                String moneda, Estado estado) {}

    Pago crear(Solicitud solicitud);
    Pago consultar(long tiendaId, UUID pagoId);
}
