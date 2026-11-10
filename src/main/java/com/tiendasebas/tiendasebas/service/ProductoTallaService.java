package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.Producto;
import com.tiendasebas.tiendasebas.entity.ProductoTalla;
import com.tiendasebas.tiendasebas.entity.Talla;
import com.tiendasebas.tiendasebas.repository.ProductoRepository;
import com.tiendasebas.tiendasebas.repository.ProductoTallaRepository;
import com.tiendasebas.tiendasebas.repository.TallaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductoTallaService {
    private final ProductoTallaRepository productoTallaRepository;
    private final ProductoRepository productoRepository;
    private final TallaRepository tallaRepository;

    public ProductoTallaService(ProductoTallaRepository productoTallaRepository,
                                ProductoRepository productoRepository,
                                TallaRepository tallaRepository) {
        this.productoTallaRepository = productoTallaRepository;
        this.productoRepository = productoRepository;
        this.tallaRepository = tallaRepository;
    }

    // =========================================================
    // LISTAR TALLAS Y STOCK DE UN PRODUCTO
    // =========================================================
    public List<ProductoTalla> listarPorProducto(Integer idProducto) {
        Long tiendaId = TenantContext.getCurrentTenant();
        Producto producto = productoRepository.findByIdProductoAndTiendaId(idProducto, tiendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (!Boolean.TRUE.equals(producto.getEstado())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no disponible");
        }
        return productoTallaRepository.findByProductoIdProductoAndProductoTiendaId(idProducto, tiendaId);
    }

    // =========================================================
    // ADMIN - INVENTARIO COMPLETO PARA REPORTES
    // =========================================================
    public List<ProductoTalla> listarInventarioCompleto() {
        return productoTallaRepository.listarInventarioCompletoPorTienda(TenantContext.getCurrentTenant());
    }

    // =========================================================
    // BUSCAR PRODUCTO + TALLA
    // =========================================================
    @Transactional
    public ProductoTalla buscar(Integer id) {
        return productoTallaRepository.buscarPorIdConBloqueo(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto con talla no encontrado"));
    }

    // =========================================================
    // REGISTRAR TALLA PARA PRODUCTO
    // =========================================================
    @Transactional
    public ProductoTalla registrar(Integer idProducto, Integer idTalla, Integer stock) {
        if (idProducto == null) throw new RuntimeException("El producto es obligatorio");
        if (idTalla == null) throw new RuntimeException("La talla es obligatoria");
        validarStock(stock);

        Long tiendaId = TenantContext.getCurrentTenant();
        Producto producto = productoRepository.findByIdProductoAndTiendaId(idProducto, tiendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        Talla talla = tallaRepository.findById(idTalla)
                .orElseThrow(() -> new RuntimeException("Talla no encontrada"));

        if (productoTallaRepository
                .findByProductoIdProductoAndTallaIdTallaAndProductoTiendaId(idProducto, idTalla, tiendaId)
                .isPresent()) {
            throw new RuntimeException("La talla ya estÃ¡ asignada a este producto");
        }

        ProductoTalla productoTalla = new ProductoTalla();
        productoTalla.setProducto(producto);
        productoTalla.setTalla(talla);
        productoTalla.setStock(stock);

        return productoTallaRepository.save(productoTalla);
    }

    // =========================================================
    // ACTUALIZAR STOCK
    // =========================================================
    @Transactional
    public ProductoTalla actualizarStock(Integer id, Integer stock) {
        validarStock(stock);

        ProductoTalla productoTalla = buscar(id);
        productoTalla.setStock(stock);

        return productoTallaRepository.save(productoTalla);
    }

    // =========================================================
    // VALIDAR STOCK
    // =========================================================
    private void validarStock(Integer stock) {
        if (stock == null) throw new RuntimeException("El stock es obligatorio");
        if (stock < 0) throw new RuntimeException("El stock no puede ser negativo");
    }
}
