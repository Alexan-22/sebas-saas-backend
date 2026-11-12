package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.transferencias.TransferenciaManualService;
import com.tiendasebas.tiendasebas.yape.PagoYapeManualService;
import com.tiendasebas.tiendasebas.dto.*;
import com.tiendasebas.tiendasebas.entity.DetalleVenta;
import com.tiendasebas.tiendasebas.entity.DireccionEnvio;
import com.tiendasebas.tiendasebas.entity.EstadoPedido;
import com.tiendasebas.tiendasebas.entity.Producto;
import com.tiendasebas.tiendasebas.entity.ProductoTalla;
import com.tiendasebas.tiendasebas.entity.TipoVenta;
import com.tiendasebas.tiendasebas.entity.Usuario;
import com.tiendasebas.tiendasebas.entity.Venta;
import com.tiendasebas.tiendasebas.exception.CompraException;
import com.tiendasebas.tiendasebas.repository.DetalleVentaRepository;
import com.tiendasebas.tiendasebas.repository.ProductoTallaRepository;
import com.tiendasebas.tiendasebas.repository.UsuarioRepository;
import com.tiendasebas.tiendasebas.repository.VentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VentaService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcVisibilidadColores;

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final ProductoTallaRepository productoTallaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DeliveryService deliveryService;
    private final ContraEntregaService contraEntregaService;
    private final CancelacionCodService cancelacionCodService;
    private final PagoYapeManualService pagoYapeManualService;
    private final TransferenciaManualService transferencias;

    public VentaService(VentaRepository ventaRepository,
                        DetalleVentaRepository detalleVentaRepository,
                        ProductoTallaRepository productoTallaRepository,
                        UsuarioRepository usuarioRepository,
                        DeliveryService deliveryService,
                        ContraEntregaService contraEntregaService,
                        CancelacionCodService cancelacionCodService,
                        PagoYapeManualService pagoYapeManualService, TransferenciaManualService transferencias) {
        this.ventaRepository = ventaRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.productoTallaRepository = productoTallaRepository;
        this.usuarioRepository = usuarioRepository;
        this.deliveryService = deliveryService;
        this.contraEntregaService = contraEntregaService;
        this.cancelacionCodService = cancelacionCodService;
        this.pagoYapeManualService = pagoYapeManualService;
        this.transferencias = transferencias;
    }

    // =========================================================
    // CLIENTE - REALIZAR COMPRA ONLINE
    // =========================================================
    @Transactional
    public CompraResponse realizarCompra(CompraRequest request, String correoUsuario) {
        Long tiendaId = TenantContext.getCurrentTenant();

        Usuario usuario = usuarioRepository.findByCorreoAndTiendaId(correoUsuario, tiendaId)
                .orElseThrow(() -> new CompraException("Usuario no encontrado"));
        validarUsuarioActivo(usuario);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new CompraException("El carrito está vacío");
        }
        String medioPago = validarMedioPagoOnline(request.getMedioPago());
        if ("YAPE".equals(medioPago)) {
            CompraResponse anterior = pagoYapeManualService.recuperarCompra(request, usuario);
            if (anterior != null) return anterior;
            pagoYapeManualService.validarHabilitado(tiendaId);
        }

        if ("TRANSFERENCIA".equals(medioPago)) {
            CompraResponse anterior=transferencias.recuperarCompra(request,usuario);
            if(anterior!=null) return anterior;
            transferencias.validarHabilitado(request.getCuentaBancariaId());
        }
        // =====================================================
        // DIRECCIÓN DE ENVÍO
        // =====================================================
        DireccionEnvioDto direccionDto = request.getDireccionEnvio();
        if (direccionDto == null) {
            throw new CompraException("La dirección de envío es obligatoria");
        }
        validarDireccionEnvio(direccionDto);

        // =====================================================
        // CALCULAR DELIVERY
        // =====================================================
        BigDecimal costoDelivery;
        try {
            costoDelivery = deliveryService.calcularCostoEnvio(
                    direccionDto.getDepartamento(),
                    direccionDto.getProvincia(),
                    direccionDto.getDistrito()
            );
        } catch (IllegalArgumentException ex) {
            throw new CompraException(ex.getMessage());
        }

        // =====================================================
        // CREAR DIRECCIÓN
        // =====================================================
        DireccionEnvio direccionEnvio = new DireccionEnvio();
        direccionEnvio.setDepartamento(direccionDto.getDepartamento().trim());
        direccionEnvio.setProvincia(direccionDto.getProvincia().trim());
        direccionEnvio.setDistrito(direccionDto.getDistrito().trim());
        direccionEnvio.setDireccionExacta(direccionDto.getDireccionExacta().trim());
        if (direccionDto.getReferencia() != null && !direccionDto.getReferencia().isBlank()) {
            direccionEnvio.setReferencia(direccionDto.getReferencia().trim());
        } else {
            direccionEnvio.setReferencia(null);
        }
        direccionEnvio.setCostoDelivery(costoDelivery);

        // =====================================================
        // CREAR VENTA ONLINE
        // =====================================================
        Venta venta = new Venta();
        venta.setTiendaId(tiendaId);
        venta.setUsuario(usuario);
        venta.setTipoVenta(TipoVenta.ONLINE);
        venta.setFechaVenta(LocalDateTime.now());
        venta.setEstado(("YAPE".equals(medioPago) || "TRANSFERENCIA".equals(medioPago)) ? "PAGO_PENDIENTE" : "REGISTRADA");
        venta.setEstadoPedido(EstadoPedido.RECIBIDO);
        venta.setMedioPago(medioPago);
        venta.setDireccionEnvio(direccionEnvio);
        venta.setSubtotal(BigDecimal.ZERO);
        venta.setCostoDelivery(costoDelivery);
        venta.setTotal(BigDecimal.ZERO);
        venta = ventaRepository.save(venta);

        // =====================================================
        // PROCESAR PRODUCTOS
        // =====================================================
        BigDecimal subtotalProductos = BigDecimal.ZERO;
        for (CompraItemDto item : request.getItems().stream()
        .sorted(java.util.Comparator.comparing(
                CompraItemDto::getIdProductoTalla,
                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
        .toList()) {
            subtotalProductos = subtotalProductos.add(
                    procesarItemVenta(venta, item.getIdProductoTalla(), item.getCantidad(), tiendaId)
            );
        }

        // =====================================================
        // TOTAL FINAL
        // =====================================================
        BigDecimal total = subtotalProductos.add(costoDelivery);
        venta.setSubtotal(subtotalProductos);
        venta.setCostoDelivery(costoDelivery);
        venta.setTotal(total);
        ventaRepository.saveAndFlush(venta);
        if ("CONTRA_ENTREGA".equals(medioPago)) {
            contraEntregaService.registrarPendiente(venta);
        }
        if ("YAPE".equals(medioPago)) {
            pagoYapeManualService.registrarPendiente(venta, usuario);
            pagoYapeManualService.recordarCompra(request, usuario, venta);
        }

        if ("TRANSFERENCIA".equals(medioPago)) {
            transferencias.registrarPendiente(venta,usuario,request.getCuentaBancariaId());
            transferencias.recordarCompra(request,usuario,venta);
        }
        return new CompraResponse(
                venta.getIdVenta(),
                subtotalProductos,
                costoDelivery,
                total,
                venta.getEstado(),
                venta.getMedioPago(),
                ("YAPE".equals(medioPago) ? "Pedido registrado. Pago Yape pendiente de verificacion del comerciante" : ("CONTRA_ENTREGA".equals(medioPago) ? "Pedido contra entrega registrado; pago pendiente" : "Compra realizada correctamente"))
        );
    }

    // =========================================================
    // PROCESAR ITEM DE VENTA
    // =========================================================
    private BigDecimal procesarItemVenta(Venta venta, Integer idProductoTalla, Integer cantidad, Long tiendaId) {
        if (cantidad == null || cantidad <= 0) {
            throw new CompraException("La cantidad debe ser mayor que cero");
        }

        if (idProductoTalla == null || idProductoTalla <= 0) {
            throw new CompraException("Producto/talla no válido");
        }

        ProductoTalla productoTalla = productoTallaRepository
                .buscarPorIdParaCompra(idProductoTalla, tiendaId)
                .orElseThrow(() -> new CompraException("Producto/talla no encontrado"));

        Producto producto = productoTalla.getProducto();

        if (!Boolean.TRUE.equals(producto.getEstado())) {
            throw new CompraException("El producto " + producto.getNombre() + " no está disponible");
        }

        if (productoTalla.getIdColor() != null) {
            Integer oculto = jdbcVisibilidadColores.queryForObject("""
                SELECT COUNT(*) FROM public.saas_producto_colores_ocultos
                WHERE tienda_id = ? AND id_producto = ? AND id_color = ?
                """, Integer.class, tiendaId, producto.getIdProducto(), productoTalla.getIdColor());
            if (oculto != null && oculto > 0) {
                throw new CompraException("Este color no esta disponible para la venta");
            }
        }
        Integer stockActual = productoTalla.getStock();
        if (stockActual == null || stockActual <= 0) {
            throw new CompraException("Sin stock disponible para: " + producto.getNombre() + " - talla " + productoTalla.getTalla().getNombreTalla());
        }
        if (stockActual < cantidad) {
            throw new CompraException("Stock insuficiente para: " + producto.getNombre() + " - talla " + productoTalla.getTalla().getNombreTalla() + ". Disponible: " + stockActual);
        }

        BigDecimal precio = obtenerPrecioVenta(producto);
        BigDecimal subtotalItem = precio.multiply(BigDecimal.valueOf(cantidad));

        DetalleVenta detalle = new DetalleVenta();
        detalle.setVenta(venta);
        detalle.setProductoTalla(productoTalla);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(precio);
        detalle.setSubtotal(subtotalItem);
        detalleVentaRepository.save(detalle);

        productoTalla.setStock(stockActual - cantidad);
        productoTallaRepository.save(productoTalla);

        return subtotalItem;
    }

    // =========================================================
    // CLIENTE - LISTAR SUS COMPRAS
    // =========================================================
    @Transactional(readOnly = true)
    public List<VentaResponse> listarMisCompras(String correoUsuario) {
        validarCorreoUsuario(correoUsuario);
        Long tiendaId = TenantContext.getCurrentTenant();

        return ventaRepository.findByUsuarioCorreoAndTiendaIdOrderByFechaVentaDesc(correoUsuario, tiendaId)
                .stream()
                .map(this::convertirVentaResponse)
                .toList();
    }

    // =========================================================
    // ADMIN - LISTAR TODAS LAS VENTAS
    // =========================================================
    @Transactional(readOnly = true)
    public List<VentaResponse> listarTodas() {
        Long tiendaId = TenantContext.getCurrentTenant();
        return ventaRepository.findByTiendaId(tiendaId)
                .stream()
                .map(this::convertirVentaResponse)
                .toList();
    }

    // =========================================================
    // ADMIN - DETALLES PARA REPORTES
    // =========================================================
    @Transactional(readOnly = true)
    public List<DetalleVentaResponse> listarDetallesParaReportes() {
        Long tiendaId = TenantContext.getCurrentTenant();
        return detalleVentaRepository.listarTodosParaReportesPorTienda(tiendaId)
                .stream()
                .map(this::convertirDetalleVentaResponse)
                .toList();
    }

    // =========================================================
    // CLIENTE - DETALLE DE SU PROPIA COMPRA
    // =========================================================
    @Transactional(readOnly = true)
    public DetalleCompraResponse obtenerDetalleMiCompra(Integer idVenta, String correoUsuario) {
        validarIdVenta(idVenta);
        validarCorreoUsuario(correoUsuario);
        Long tiendaId = TenantContext.getCurrentTenant();

        Venta venta = ventaRepository.findByIdVentaAndUsuarioCorreoAndTiendaId(idVenta, correoUsuario, tiendaId)
                .orElseThrow(() -> new CompraException("La compra no existe o no pertenece al usuario"));
        return construirDetalleCompra(venta);
    }

    // =========================================================
    // ADMIN - DETALLE DE CUALQUIER VENTA
    // =========================================================
    @Transactional(readOnly = true)
    public DetalleCompraResponse obtenerDetalleVentaAdmin(Integer idVenta) {
        validarIdVenta(idVenta);
        Long tiendaId = TenantContext.getCurrentTenant();

        Venta venta = ventaRepository.findByIdVentaAndTiendaId(idVenta, tiendaId)
                .orElseThrow(() -> new CompraException("La venta no existe"));
        return construirDetalleCompra(venta);
    }

    // =========================================================
    // ADMIN - ACTUALIZAR ESTADO LOGÍSTICO
    // =========================================================
    @Transactional
    public DetalleCompraResponse actualizarEstadoPedido(Integer idVenta, String nuevoEstado) {
        validarIdVenta(idVenta);
        if (nuevoEstado == null || nuevoEstado.isBlank()) {
            throw new CompraException("El estado del pedido es obligatorio");
        }
        EstadoPedido estadoPedido;
        try {
            estadoPedido = EstadoPedido.valueOf(nuevoEstado.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new CompraException("Estado de pedido no válido");
        }

        Long tiendaId = TenantContext.getCurrentTenant();
        Venta venta = ventaRepository.buscarPorIdYTiendaConBloqueo(idVenta, tiendaId)
                .orElseThrow(() -> new CompraException("La venta no existe"));



        EstadoPedido estadoActual = obtenerEstadoPedidoSeguro(venta);
        validarTransicionEstado(estadoActual, estadoPedido);
        // La ruta generica no puede cancelar sin reponer stock.
        if (estadoPedido == EstadoPedido.CANCELADO) {
            throw new CompraException("Cancelacion generica bloqueada para proteger el stock. COD pendiente: utilizar cancelacion dedicada; otros pagos requieren devolucion supervisada.");
        }
        cancelacionCodService.validarEstadoComercialCod(venta, estadoPedido);
        if ("YAPE".equals(venta.getMedioPago())
                && estadoPedido != EstadoPedido.RECIBIDO) {
            pagoYapeManualService.exigirAprobado(venta.getIdVenta());
        }
        if ("TRANSFERENCIA".equals(venta.getMedioPago()) && estadoPedido!=EstadoPedido.RECIBIDO) transferencias.exigirAprobado(idVenta);
        venta.setEstadoPedido(estadoPedido);
        ventaRepository.save(venta);

        return construirDetalleCompra(venta);
    }

    // =========================================================
    // CONVERTIR VENTA A DTO
    // =========================================================
    private VentaResponse convertirVentaResponse(Venta venta) {
        Usuario usuario = venta.getUsuario();

        Integer idUsuario = usuario != null
                ? usuario.getIdUsuario()
                : null;

        String nombreCliente = usuario != null
                ? usuario.getNombre()
                : "Cliente";

        String apellidoCliente = usuario != null
                ? usuario.getApellido()
                : null;

        String correoCliente = usuario != null
                ? usuario.getCorreo()
                : null;

        return new VentaResponse(
                venta.getIdVenta(),
                obtenerTipoVentaSeguro(venta).name(),
                idUsuario,

                nombreCliente,
                apellidoCliente,
                correoCliente,
                null,
                null,
                venta.getFechaVenta(),
                venta.getTotal(),
                venta.getEstado(),
                obtenerEstadoPedidoSeguro(venta).name(),
                venta.getMedioPago()
        );
    }

    // =========================================================
    // CONSTRUIR RESPUESTA DEL DETALLE
    // =========================================================
    private DetalleCompraResponse construirDetalleCompra(Venta venta) {
        List<DetalleVentaResponse> detalles = detalleVentaRepository
                .findByVentaOrderByIdDetalleVentaAsc(venta)
                .stream()
                .map(this::convertirDetalleVentaResponse)
                .toList();

        Usuario usuario = venta.getUsuario();

        BigDecimal subtotal = obtenerSubtotalSeguro(venta, detalles);

        BigDecimal costoDelivery = venta.getCostoDelivery() != null
                ? venta.getCostoDelivery()
                : BigDecimal.ZERO;

        DireccionEnvioResponse direccionEnvio =
                convertirDireccionEnvioResponse(venta.getDireccionEnvio());

        return new DetalleCompraResponse(
                venta.getIdVenta(),
                venta.getFechaVenta(),
                obtenerTipoVentaSeguro(venta).name(),
                subtotal,
                costoDelivery,
                venta.getTotal(),
                venta.getEstado(),
                obtenerEstadoPedidoSeguro(venta).name(),
                venta.getMedioPago(),
                usuario != null ? usuario.getIdUsuario() : null,

                usuario != null ? usuario.getNombre() : "Cliente",
                usuario != null ? usuario.getApellido() : null,
                usuario != null ? usuario.getCorreo() : null,
                null,
                null,
                null,
                direccionEnvio,
                detalles
        );
    }

    private BigDecimal obtenerSubtotalSeguro(Venta venta, List<DetalleVentaResponse> detalles) {
        if (venta.getSubtotal() != null) {
            return venta.getSubtotal();
        }
        return detalles.stream()
                .map(DetalleVentaResponse::getSubtotal)
                .filter(sub -> sub != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private DireccionEnvioResponse convertirDireccionEnvioResponse(DireccionEnvio direccion) {
        if (direccion == null) {
            return null;
        }
        return new DireccionEnvioResponse(
                direccion.getId(),
                direccion.getDepartamento(),
                direccion.getProvincia(),
                direccion.getDistrito(),
                direccion.getDireccionExacta(),
                direccion.getReferencia(),
                direccion.getCostoDelivery()
        );
    }

    private DetalleVentaResponse convertirDetalleVentaResponse(DetalleVenta detalle) {
        return new DetalleVentaResponse(
                detalle.getIdDetalleVenta(),
                detalle.getVenta().getIdVenta(),
                detalle.getProductoTalla(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getSubtotal()
        );
    }

    private void validarUsuarioActivo(Usuario usuario) {
        if (usuario == null || !Boolean.TRUE.equals(usuario.getEstado())) {
            throw new CompraException("El usuario está deshabilitado");
        }
    }

    private String validarMedioPagoOnline(String medioPago) {
        if (medioPago == null || medioPago.isBlank()) {
            throw new CompraException("Debe seleccionar un medio de pago");
        }
        String medioPagoNormalizado = medioPago.trim().toUpperCase();
        if (!medioPagoNormalizado.equals("YAPE") && !medioPagoNormalizado.equals("TRANSFERENCIA")) {
            throw new CompraException("Medio de pago no válido");
        }
        return medioPagoNormalizado;
    }

    private void validarIdVenta(Integer idVenta) {
        if (idVenta == null || idVenta <= 0) {
            throw new CompraException("Venta no válida");
        }
    }

    private void validarCorreoUsuario(String correoUsuario) {
        if (correoUsuario == null || correoUsuario.isBlank()) {
            throw new CompraException("Usuario no autenticado");
        }
    }

    private void validarDireccionEnvio(DireccionEnvioDto direccion) {
        if (direccion.getDepartamento() == null || direccion.getDepartamento().isBlank()) {
            throw new CompraException("El departamento es obligatorio");
        }
        if (direccion.getProvincia() == null || direccion.getProvincia().isBlank()) {
            throw new CompraException("La provincia es obligatoria");
        }
        if (direccion.getDistrito() == null || direccion.getDistrito().isBlank()) {
            throw new CompraException("El distrito es obligatorio");
        }
        if (direccion.getDireccionExacta() == null || direccion.getDireccionExacta().isBlank()) {
            throw new CompraException("La dirección exacta es obligatoria");
        }
    }

    private TipoVenta obtenerTipoVentaSeguro(Venta venta) {
        return venta.getTipoVenta() != null
                ? venta.getTipoVenta()
                : TipoVenta.ONLINE;
    }

    private EstadoPedido obtenerEstadoPedidoSeguro(Venta venta) {
        return venta.getEstadoPedido() != null ? venta.getEstadoPedido() : EstadoPedido.RECIBIDO;
    }

    private void validarTransicionEstado(EstadoPedido actual, EstadoPedido nuevo) {
        if (actual == nuevo) {
            return;
        }
        boolean permitida = switch (actual) {
            case RECIBIDO -> nuevo == EstadoPedido.EN_PREPARACION || nuevo == EstadoPedido.CANCELADO;
            case EN_PREPARACION -> nuevo == EstadoPedido.ENVIADO || nuevo == EstadoPedido.CANCELADO;
            case ENVIADO -> nuevo == EstadoPedido.ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };
        if (!permitida) {
            throw new CompraException("No se puede cambiar el estado de " + actual + " a " + nuevo);
        }
    }

    private BigDecimal obtenerPrecioVenta(Producto producto) {
        BigDecimal precioNormal = producto.getPrecio();
        BigDecimal precioOferta = producto.getPrecioOferta();
        if (precioNormal == null || precioNormal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CompraException("El producto " + producto.getNombre() + " no tiene un precio válido");
        }
        if (precioOferta != null && precioOferta.compareTo(BigDecimal.ZERO) > 0 && precioOferta.compareTo(precioNormal) < 0) {
            return precioOferta;
        }
        return precioNormal;
    }
}
