package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.Producto;
import com.tiendasebas.tiendasebas.entity.ProductoImagen;
import com.tiendasebas.tiendasebas.repository.ProductoImagenRepository;
import com.tiendasebas.tiendasebas.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductoImagenService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcVisibilidadImagenes;

    private final ProductoImagenRepository productoImagenRepository;
    private final ProductoRepository productoRepository;
    private final SupabaseStorageService supabaseStorageService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.storage.provider:local}")
    private String storageProvider;

    private static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");

    public ProductoImagenService(ProductoImagenRepository productoImagenRepository,
                                 ProductoRepository productoRepository,
                                 SupabaseStorageService supabaseStorageService) {
        this.productoImagenRepository = productoImagenRepository;
        this.productoRepository = productoRepository;
        this.supabaseStorageService = supabaseStorageService;
    }

    // ============================================================
    // LISTAR IMÃGENES
    // ============================================================
    public List<ProductoImagen> listarPorProducto(Integer idProducto) {
        Long tiendaId = TenantContext.getCurrentTenant();
        Producto producto = productoRepository.findByIdProductoAndTiendaId(idProducto, tiendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (!Boolean.TRUE.equals(producto.getEstado())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no disponible");
        }
        return productoImagenRepository.findByProductoIdProductoAndProductoTiendaIdOrderByOrdenAsc(idProducto, tiendaId)
            .stream().filter(imagen -> {
                if (imagen.getIdColor() == null) return true;
                Integer oculto = jdbcVisibilidadImagenes.queryForObject("""
                    SELECT COUNT(*) FROM public.saas_producto_colores_ocultos
                    WHERE tienda_id=? AND id_producto=? AND id_color=?
                    """, Integer.class, tiendaId, idProducto, imagen.getIdColor());
                return oculto == null || oculto == 0;
            }).toList();
    }


    public List<ProductoImagen> listarPorProductoAdmin(Integer idProducto) {
        Long tiendaId = TenantContext.getCurrentTenant();
        productoRepository.findByIdProductoAndTiendaId(idProducto, tiendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return productoImagenRepository.findByProductoIdProductoAndProductoTiendaIdOrderByOrdenAsc(idProducto, tiendaId);
    }

    // ============================================================
    // SUBIR Y REGISTRAR IMAGEN
    // ============================================================
    public ProductoImagen subir(Integer idProducto, MultipartFile archivo, Integer orden) {
        Long tiendaId = TenantContext.getCurrentTenant();
        Producto producto = productoRepository.findByIdProductoAndTiendaId(idProducto, tiendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        validarArchivo(archivo);
        // 12 fotografías en total, contando la portada del producto.
        // El contador se valida también en backend, no solo Angular.
        long adicionales = productoImagenRepository.countByProductoIdProductoAndProductoTiendaId(idProducto, tiendaId);
        long portada = producto.getImagen() == null || producto.getImagen().isBlank() ? 0 : 1;
        if (adicionales + portada >= 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo 12 fotografías por producto");
        }
        if (orden == null || orden < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Orden inválido");
        }

        String extension = obtenerExtension(archivo.getOriginalFilename());
        String nombreArchivo = UUID.randomUUID() + "." + extension;
        boolean supabase = "supabase".equalsIgnoreCase(storageProvider);

        if (supabase) {
            // ProducciÃ³n: guardar en Supabase Storage
            supabaseStorageService.subir(archivo, "productos/" + nombreArchivo);
        } else if ("local".equalsIgnoreCase(storageProvider)) {
            // Desarrollo: conservar almacenamiento local
            try {
                Path directorio = Paths.get(uploadDir).toAbsolutePath().normalize();
                Files.createDirectories(directorio);
                Path destino = directorio.resolve(nombreArchivo).normalize();

                // Seguridad: evita salir del directorio uploads
                if (!destino.startsWith(directorio)) {
                    throw new RuntimeException("Ruta de archivo no permitida");
                }

                Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Error al guardar la imagen", e);
            }
        } else {
            throw new IllegalStateException("Proveedor de almacenamiento no vÃ¡lido");
        }

        ProductoImagen productoImagen = new ProductoImagen();
        productoImagen.setProducto(producto);
        productoImagen.setImagen(nombreArchivo);
        productoImagen.setOrden(orden != null ? orden : 0);

        try {
            return productoImagenRepository.save(productoImagen);
        } catch (RuntimeException e) {
            // Evitar dejar archivos huÃ©rfanos si falla PostgreSQL
            try {
                if (supabase) {
                    supabaseStorageService.eliminar("productos/" + nombreArchivo);
                } else {
                    Files.deleteIfExists(Paths.get(uploadDir).toAbsolutePath()
                            .normalize().resolve(nombreArchivo));
                }
            } catch (Exception cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }


    // ============================================================
    // ELIMINAR IMAGEN
    // ============================================================
    public void eliminar(Integer idImagen) {
        Long tiendaId = TenantContext.getCurrentTenant();
        ProductoImagen imagen = productoImagenRepository.findById(idImagen)
                .filter(i -> i.getProducto() != null && tiendaId.equals(i.getProducto().getTiendaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));

        if ("supabase".equalsIgnoreCase(storageProvider)) {
            // ProducciÃ³n: eliminar imagen de Supabase Storage
            supabaseStorageService.eliminar("productos/" + imagen.getImagen());

        } else if ("local".equalsIgnoreCase(storageProvider)) {
            // Desarrollo: eliminar imagen local
            try {
                Path directorio = Paths.get(uploadDir).toAbsolutePath().normalize();
                Path archivo = directorio.resolve(imagen.getImagen()).normalize();

                if (!archivo.startsWith(directorio)) {
                    throw new RuntimeException("Ruta de archivo no permitida");
                }

                Files.deleteIfExists(archivo);

            } catch (IOException e) {
                throw new RuntimeException("No se pudo eliminar el archivo de imagen", e);
            }
        } else {
            throw new IllegalStateException("Proveedor de almacenamiento no vÃ¡lido");
        }

        productoImagenRepository.delete(imagen);
    }

    // ============================================================
    // VALIDACIONES
    // ============================================================
    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new RuntimeException("Debe seleccionar una imagen");
        }

        if (archivo.getContentType() == null || !TIPOS_PERMITIDOS.contains(archivo.getContentType())) {
            throw new RuntimeException("Solo se permiten imÃ¡genes JPG, PNG o WEBP");
        }

        // MÃ¡ximo 5 MB por imagen
        if (archivo.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("La imagen no puede superar los 5 MB");
        }

        String extension = obtenerExtension(archivo.getOriginalFilename());

        if (!Set.of("jpg", "jpeg", "png", "webp").contains(extension)) {
            throw new RuntimeException("ExtensiÃ³n de imagen no permitida");
        }
    }

    private String obtenerExtension(String nombre) {
        if (nombre == null || !nombre.contains(".")) {
            throw new RuntimeException("El archivo no tiene una extensiÃ³n vÃ¡lida");
        }

        return nombre.substring(nombre.lastIndexOf(".") + 1).toLowerCase();
    }
}
