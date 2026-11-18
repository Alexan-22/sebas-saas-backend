
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;

import com.tiendasebas.tiendasebas.entity.Categoria;
import com.tiendasebas.tiendasebas.entity.Genero;
import com.tiendasebas.tiendasebas.entity.Producto;

import com.tiendasebas.tiendasebas.repository.CategoriaRepository;
import com.tiendasebas.tiendasebas.repository.GeneroRepository;
import com.tiendasebas.tiendasebas.repository.ProductoRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final GeneroRepository generoRepository;

    public ProductoService(
            ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository,
            GeneroRepository generoRepository
    ) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.generoRepository = generoRepository;
    }

    // =========================================================
    // CATALOGO PUBLICO - SOLO PRODUCTOS ACTIVOS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> listar() {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByEstadoTrueAndTiendaId(tiendaId);
    }

    // =========================================================
    // ADMIN - TODOS LOS PRODUCTOS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> listarAdmin() {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository.findByTiendaId(tiendaId);
    }

    // =========================================================
    // ADMIN - BUSCAR PRODUCTO
    // =========================================================

    @Transactional(readOnly = true)
    public Producto buscarAdmin(Integer id) {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByIdProductoAndTiendaId(id, tiendaId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Producto no encontrado"
                        )
                );
    }

    // =========================================================
    // PUBLICO - BUSCAR PRODUCTO ACTIVO
    // =========================================================

    @Transactional(readOnly = true)
    public Producto buscar(Integer id) {

        Long tiendaId = TenantContext.getCurrentTenant();

        Producto producto = productoRepository
                .findByIdProductoAndTiendaId(id, tiendaId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Producto no encontrado"
                        )
                );

        // No mostrar productos desactivados.
        if (!Boolean.TRUE.equals(producto.getEstado())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto no disponible"
            );
        }

        return producto;
    }

    // =========================================================
    // PUBLICO - FILTRAR POR GENERO
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> listarPorGenero(Integer idGenero) {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByEstadoTrueAndGeneroIdGeneroAndTiendaId(
                        idGenero,
                        tiendaId
                );
    }

    // =========================================================
    // PUBLICO - FILTRAR POR CATEGORIA
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> listarPorCategoria(Integer idCategoria) {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByEstadoTrueAndCategoriaIdCategoriaAndTiendaId(
                        idCategoria,
                        tiendaId
                );
    }

    // =========================================================
    // PUBLICO - FILTRAR POR GENERO Y CATEGORIA
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> filtrar(
            Integer idGenero,
            Integer idCategoria
    ) {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByEstadoTrueAndGeneroIdGeneroAndCategoriaIdCategoriaAndTiendaId(
                        idGenero,
                        idCategoria,
                        tiendaId
                );
    }

    // =========================================================
    // PUBLICO - BUSCAR POR NOMBRE
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> buscarPorNombre(String texto) {

        if (texto == null || texto.isBlank()) {
            return listar();
        }

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByEstadoTrueAndNombreContainingIgnoreCaseAndTiendaId(
                        texto.trim(),
                        tiendaId
                );
    }

    // =========================================================
    // PUBLICO - PRODUCTOS DESTACADOS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> listarDestacados() {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByEstadoTrueAndDestacadoTrueAndTiendaId(
                        tiendaId
                );
    }

    // =========================================================
    // PUBLICO - PRODUCTOS NUEVOS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> listarNuevos() {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByEstadoTrueAndNuevoTrueAndTiendaId(
                        tiendaId
                );
    }

    // =========================================================
    // PUBLICO - PRODUCTOS EN OFERTA
    // =========================================================

    @Transactional(readOnly = true)
    public List<Producto> listarOfertas() {

        Long tiendaId = TenantContext.getCurrentTenant();

        return productoRepository
                .findByEstadoTrueAndPrecioOfertaIsNotNullAndTiendaId(
                        tiendaId
                );
    }

    // =========================================================
    // ADMIN - CREAR PRODUCTO
    // =========================================================

    @Transactional
    public Producto guardar(
            Producto producto,
            Integer idGenero,
            Integer idCategoria
    ) {

        validarProducto(producto);

        Long tiendaId = TenantContext.getCurrentTenant();

        Genero genero = generoRepository
                .findById(idGenero)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Género no encontrado"
                        )
                );

        Categoria categoria = categoriaRepository
                .findByIdCategoriaAndTiendaId(
                        idCategoria,
                        tiendaId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Categoría no encontrada"
                        )
                );

        normalizarProducto(producto);

        // Impedir asignar manualmente el ID.
        producto.setIdProducto(null);

        // Asociar siempre al tenant autenticado.
        producto.setTiendaId(tiendaId);

        producto.setGenero(genero);
        producto.setCategoria(categoria);

        producto.setEstado(
                producto.getEstado() == null ||
                        Boolean.TRUE.equals(producto.getEstado())
        );

        producto.setNuevo(
                Boolean.TRUE.equals(producto.getNuevo())
        );

        producto.setDestacado(
                Boolean.TRUE.equals(producto.getDestacado())
        );

        return productoRepository.save(producto);
    }

    // =========================================================
    // ADMIN - ACTUALIZAR PRODUCTO
    // =========================================================

    @Transactional
    public Producto actualizar(
            Integer id,
            Producto datos,
            Integer idGenero,
            Integer idCategoria
    ) {

        Long tiendaId = TenantContext.getCurrentTenant();

        Producto producto = productoRepository
                .findByIdProductoAndTiendaId(id, tiendaId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Producto no encontrado"
                        )
                );

        validarProducto(datos);

        Genero genero = generoRepository
                .findById(idGenero)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Género no encontrado"
                        )
                );

        Categoria categoria = categoriaRepository
                .findByIdCategoriaAndTiendaId(
                        idCategoria,
                        tiendaId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Categoría no encontrada"
                        )
                );

        normalizarProducto(datos);

        producto.setNombre(datos.getNombre());
        producto.setDescripcion(datos.getDescripcion());

        producto.setPrecio(datos.getPrecio());
        producto.setPrecioOferta(datos.getPrecioOferta());

        producto.setImagen(datos.getImagen());

        producto.setNuevo(
                Boolean.TRUE.equals(datos.getNuevo())
        );

        producto.setDestacado(
                Boolean.TRUE.equals(datos.getDestacado())
        );

        producto.setGenero(genero);
        producto.setCategoria(categoria);

        // Permitir reactivar o desactivar.
        if (datos.getEstado() != null) {
            producto.setEstado(datos.getEstado());
        }

        return productoRepository.save(producto);
    }

    // =========================================================
    // ADMIN - DESACTIVAR PRODUCTO
    // =========================================================

    @Transactional
    public void eliminar(Integer id) {

        Long tiendaId = TenantContext.getCurrentTenant();

        Producto producto = productoRepository
                .findByIdProductoAndTiendaId(id, tiendaId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Producto no encontrado"
                        )
                );

        // Borrado lógico:
        // conservar producto, ventas e historial.
        producto.setEstado(false);

        productoRepository.save(producto);
    }

    // =========================================================
    // VALIDAR PRODUCTO
    // =========================================================

    private void validarProducto(Producto producto) {

        if (producto == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los datos del producto son obligatorios"
            );
        }

        if (
                producto.getNombre() == null ||
                        producto.getNombre().isBlank()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nombre del producto es obligatorio"
            );
        }

        if (producto.getNombre().trim().length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nombre no puede superar los 150 caracteres"
            );
        }

        if (
                producto.getDescripcion() != null &&
                        producto.getDescripcion().trim().length() > 1000
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La descripción no puede superar los 1000 caracteres"
            );
        }

        if (
                producto.getImagen() != null &&
                        producto.getImagen().trim().length() > 255
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nombre de la imagen no puede superar los 255 caracteres"
            );
        }

        if (
                producto.getPrecio() == null ||
                        producto.getPrecio().compareTo(BigDecimal.ZERO) <= 0
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El precio debe ser mayor que cero"
            );
        }

        if (producto.getPrecioOferta() != null) {

            if (
                    producto.getPrecioOferta()
                            .compareTo(BigDecimal.ZERO) <= 0
            ) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El precio de oferta debe ser mayor que cero"
                );
            }

            if (
                    producto.getPrecioOferta()
                            .compareTo(producto.getPrecio()) >= 0
            ) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El precio de oferta debe ser menor al precio normal"
                );
            }
        }
    }

    // =========================================================
    // NORMALIZAR PRODUCTO
    // =========================================================

    private void normalizarProducto(Producto producto) {

        producto.setNombre(
                producto.getNombre().trim()
        );

        if (producto.getDescripcion() != null) {

            String descripcion =
                    producto.getDescripcion().trim();

            producto.setDescripcion(
                    descripcion.isEmpty() ? null : descripcion
            );
        }

        if (producto.getImagen() != null) {

            String imagen =
                    producto.getImagen().trim();

            producto.setImagen(
                    imagen.isEmpty() ? null : imagen
            );
        }
    }
}
