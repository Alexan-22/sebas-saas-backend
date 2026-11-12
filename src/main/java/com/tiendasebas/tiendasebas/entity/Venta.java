package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ventas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idVenta;

    /*
     * =========================================================
     * CLIENTE ONLINE
     * =========================================================
     *
     * Se utiliza cuando tipoVenta = ONLINE.
     *
     * Ahora puede ser NULL porque una venta PRESENCIAL
     * no necesita una cuenta registrada en usuarios.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    /*
     * =========================================================
     * CLIENTE PRESENCIAL
     * =========================================================
     *
     * Se utiliza cuando tipoVenta = PRESENCIAL.
     *
     * Este cliente pertenece al flujo físico/POS y no
     * necesita usuario, contraseña ni acceso a la tienda web.
     */


    /*
     * =========================================================
     * TIPO DE VENTA
     * =========================================================
     *
     * ONLINE:
     * - Usuario registrado
     * - Delivery
     * - YAPE / TARJETA
     *
     * PRESENCIAL:
     * - Cliente presencial
     * - Sin delivery
     * - EFECTIVO / YAPE / TARJETA
     *
     * Se deja nullable para conservar compatibilidad con
     * ventas históricas existentes.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_venta", length = 20)
    private TipoVenta tipoVenta;

    /*
     * =========================================================
     * FECHA
     * =========================================================
     */
    @Column(nullable = false)
    private LocalDateTime fechaVenta;

    /*
     * =========================================================
     * MONTOS
     * =========================================================
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 12, scale = 2)
    private BigDecimal costoDelivery;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /*
     * =========================================================
     * ESTADO COMERCIAL
     * =========================================================
     *
     * Se conserva para mantener compatibilidad con las
     * ventas existentes.
     */
    @Column(nullable = false, length = 50)
    private String estado;

    /*
     * =========================================================
     * ESTADO DEL PEDIDO
     * =========================================================
     *
     * ONLINE:
     * normalmente comienza en RECIBIDO.
     *
     * PRESENCIAL:
     * normalmente se registra directamente como ENTREGADO.
     *
     * Las ventas históricas pueden tener NULL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pedido", length = 30)
    private EstadoPedido estadoPedido;

    /*
     * =========================================================
     * MEDIO DE PAGO
     * =========================================================
     *
     * ONLINE:
     * YAPE / TARJETA
     *
     * PRESENCIAL:
     * EFECTIVO / YAPE / TARJETA
     */
    @Column(nullable = false, length = 50)
    private String medioPago;

    /*
     * =========================================================
     * DIRECCIÓN DE ENVÍO
     * =========================================================
     *
     * ONLINE:
     * contiene la dirección de entrega.
     *
     * PRESENCIAL:
     * permanece NULL.
     */
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "id_direccion_envio")
    private DireccionEnvio direccionEnvio;

    /*
     * =========================================================
     * DETALLE DE VENTA
     * =========================================================
     */
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles;

    //Mapeo de tienda_id en Entidades Existentes
    @Column(name = "tienda_id", nullable = false)
    private Long tiendaId;

    @PrePersist
    @PreUpdate
    private void validarTienda() {
        if (tiendaId == null || tiendaId <= 0) {
            throw new IllegalStateException("La venta requiere una tienda valida");
        }
    }
}
