package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.Categoria;
import com.tiendasebas.tiendasebas.entity.Genero;
import com.tiendasebas.tiendasebas.entity.Producto;
import com.tiendasebas.tiendasebas.repository.CategoriaRepository;
import com.tiendasebas.tiendasebas.repository.GeneroRepository;
import com.tiendasebas.tiendasebas.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final GeneroRepository generoRepository;

    public ProductoService(ProductoRepository productoRepository,
                           CategoriaRepository categoriaRepository,
                           GeneroRepository generoRepository) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.generoRepository = generoRepository;
    }

    public List<Producto> listar() {
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByEstadoTrueAndTiendaId(tiendaId);
    }

    public List<Producto> listarAdmin() {
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByTiendaId(tiendaId);
    }

    public Producto buscarAdmin(Integer id) {
        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByIdProductoAndTiendaId(id, tiendaId)
                .orElseThrow(() ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Producto no encontrado"
                    )
                );
    }

    public Producto buscar(Integer id) {
        Long tiendaId = TenantContext.getCurrentTenant();
        Producto producto = productoRepository.findByIdProductoAndTiendaId(id, tiendaId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (!Boolean.TRUE.equals(producto.getEstado())) {
            throw new RuntimeException("Producto no disponible");
        }

        return producto;
    }

    public List<Producto> listarPorGenero(Integer idGenero) {
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByEstadoTrueAndGeneroIdGeneroAndTiendaId(idGenero, tiendaId);
    }

    public List<Producto> listarPorCategoria(Integer idCategoria) {
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByEstadoTrueAndCategoriaIdCategoriaAndTiendaId(idCategoria, tiendaId);
    }

    public List<Producto> filtrar(Integer idGenero, Integer idCategoria) {
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByEstadoTrueAndGeneroIdGeneroAndCategoriaIdCategoriaAndTiendaId(idGenero, idCategoria, tiendaId);
    }

    public List<Producto> buscarPorNombre(String texto) {
        if (texto == null || texto.isBlank()) {
            return listar();
        }
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByEstadoTrueAndNombreContainingIgnoreCaseAndTiendaId(texto.trim(), tiendaId);
    }

    public List<Producto> listarDestacados() {
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByEstadoTrueAndDestacadoTrueAndTiendaId(tiendaId);
    }

    public List<Producto> listarNuevos() {
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByEstadoTrueAndNuevoTrueAndTiendaId(tiendaId);
    }

    public List<Producto> listarOfertas() {
        Long tiendaId = TenantContext.getCurrentTenant();
        return productoRepository.findByEstadoTrueAndPrecioOfertaIsNotNullAndTiendaId(tiendaId);
    }

    public Producto guardar(Producto producto, Integer idGenero, Integer idCategoria) {
        validarProducto(producto);
        Long tiendaId = TenantContext.getCurrentTenant();

        Genero genero = generoRepository.findById(idGenero)
                .orElseThrow(() -> new RuntimeException("Género no encontrado"));

        Categoria categoria = categoriaRepository.findByIdCategoriaAndTiendaId(idCategoria, tiendaId)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        normalizarProducto(producto);
        producto.setIdProducto(null);

        producto.setTiendaId(tiendaId);
        producto.setGenero(genero);
        producto.setCategoria(categoria);
        producto.setEstado(producto.getEstado() == null || Boolean.TRUE.equals(producto.getEstado()));
        producto.setNuevo(Boolean.TRUE.equals(producto.getNuevo()));
        producto.setDestacado(Boolean.TRUE.equals(producto.getDestacado()));

        return productoRepository.save(producto);
    }

    public Producto actualizar(Integer id, Producto datos, Integer idGenero, Integer idCategoria) {
        Long tiendaId = TenantContext.getCurrentTenant();
        Producto producto = productoRepository.findByIdProductoAndTiendaId(id, tiendaId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        validarProducto(datos);

        Genero genero = generoRepository.findById(idGenero)
                .orElseThrow(() -> new RuntimeException("Género no encontrado"));

        Categoria categoria = categoriaRepository.findByIdCategoriaAndTiendaId(idCategoria, tiendaId)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        normalizarProducto(datos);

        producto.setNombre(datos.getNombre());
        producto.setDescripcion(datos.getDescripcion());
        producto.setPrecio(datos.getPrecio());
        producto.setPrecioOferta(datos.getPrecioOferta());
        producto.setImagen(datos.getImagen());
        producto.setNuevo(Boolean.TRUE.equals(datos.getNuevo()));
        producto.setDestacado(Boolean.TRUE.equals(datos.getDestacado()));
        producto.setGenero(genero);
        producto.setCategoria(categoria);

        if (datos.getEstado() != null) {
            producto.setEstado(datos.getEstado());
        }

        return productoRepository.save(producto);
    }

    public void eliminar(Integer id) {
        Long tiendaId = TenantContext.getCurrentTenant();
        Producto producto = productoRepository.findByIdProductoAndTiendaId(id, tiendaId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        producto.setEstado(false);
        productoRepository.save(producto);
    }

    // =========================================================
    // VALIDACIONES
    // =========================================================
    private void validarProducto(Producto producto) {
        if (producto == null) {
            throw new RuntimeException("Los datos del producto son obligatorios");
        }

        if (producto.getNombre() == null || producto.getNombre().isBlank()) {
            throw new RuntimeException("El nombre del producto es obligatorio");
        }

        if (producto.getNombre().trim().length() > 150) {
            throw new RuntimeException("El nombre no puede superar los 150 caracteres");
        }

        if (producto.getDescripcion() != null && producto.getDescripcion().trim().length() > 1000) {
            throw new RuntimeException("La descripción no puede superar los 1000 caracteres");
        }

        if (producto.getImagen() != null && producto.getImagen().trim().length() > 255) {
            throw new RuntimeException("El nombre de la imagen no puede superar los 255 caracteres");
        }

        if (producto.getPrecio() == null || producto.getPrecio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El precio debe ser mayor que cero");
        }

        if (producto.getPrecioOferta() != null) {
            if (producto.getPrecioOferta().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("El precio de oferta debe ser mayor que cero");
            }

            if (producto.getPrecioOferta().compareTo(producto.getPrecio()) >= 0) {
                throw new RuntimeException("El precio de oferta debe ser menor al precio normal");
            }
        }
    }

    // =========================================================
    // NORMALIZACIÓN
    // =========================================================
    private void normalizarProducto(Producto producto) {
        producto.setNombre(producto.getNombre().trim());

        if (producto.getDescripcion() != null) {
            String descripcion = producto.getDescripcion().trim();
            producto.setDescripcion(descripcion.isEmpty() ? null : descripcion);
        }

        if (producto.getImagen() != null) {
            String imagen = producto.getImagen().trim();
            producto.setImagen(imagen.isEmpty() ? null : imagen);
        }
    }
}