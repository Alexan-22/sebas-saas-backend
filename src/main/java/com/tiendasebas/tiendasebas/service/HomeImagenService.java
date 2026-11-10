
package com.tiendasebas.tiendasebas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class HomeImagenService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.storage.provider:local}")
    private String storageProvider;

    private final SupabaseStorageService supabaseStorageService;

    public HomeImagenService(SupabaseStorageService supabaseStorageService) {
        this.supabaseStorageService = supabaseStorageService;
    }

    /*
     * Tipos MIME permitidos.
     * JFIF normalmente llega desde el navegador
     * como image/jpeg, por eso comparte MIME
     * con JPG y JPEG.
     */
    private static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    /*
     * Extensiones permitidas.
     */
    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of("jpg", "jpeg", "jfif", "png", "webp");

    /*
     * Tamaño máximo:
     * 5 MB.
     */
    private static final long TAMANO_MAXIMO = 5L * 1024L * 1024L;

    // =========================================================
    // BANNER
    // =========================================================
    public String guardarBanner(MultipartFile archivo) {
        return guardar(archivo, "home/banner");
    }

    // =========================================================
    // COLECCIÓN
    // =========================================================
    public String guardarColeccion(MultipartFile archivo) {
        return guardar(archivo, "home/colecciones");
    }

    // =========================================================
    // GUARDAR ARCHIVO
    // =========================================================
    private String guardar(MultipartFile archivo, String subcarpeta) {
        validarArchivo(archivo);

        /*
         * Obtener extensión validada.
         * Generamos un nombre único para
         * evitar sobrescribir imágenes.
         */
        String extension = obtenerExtension(archivo.getOriginalFilename());
        String nombreArchivo = UUID.randomUUID() + "." + extension;
        String rutaRelativa = subcarpeta + "/" + nombreArchivo;

        // Producción: guardar en Supabase Storage.
        if ("supabase".equalsIgnoreCase(storageProvider)) {
            supabaseStorageService.subir(archivo, "productos/" + rutaRelativa);
            return rutaRelativa;
        }

        if (!"local".equalsIgnoreCase(storageProvider)) {
            throw new IllegalStateException("Proveedor de almacenamiento no válido");
        }

        try {
            /*
             * Directorio configurado en:
             * application.properties
             * app.upload.dir=uploads/productos
             */
            Path directorioBase = Paths.get(uploadDir).toAbsolutePath().normalize();

            /*
             * Ejemplos:
             * uploads/productos/home/banner
             * uploads/productos/home/colecciones
             */
            Path directorio = directorioBase.resolve(subcarpeta).normalize();

            /*
             * Seguridad:
             * impedimos que una ruta salga
             * del directorio configurado.
             */
            if (!directorio.startsWith(directorioBase)) {
                throw new RuntimeException("Ruta de archivo no permitida");
            }

            /*
             * Crear directorios si todavía
             * no existen.
             */
            Files.createDirectories(directorio);

            /*
             * Ejemplo:
             * 1e8d...-42a1.jfif
             */
            Path destino = directorio.resolve(nombreArchivo).normalize();

            /*
             * Segunda validación de seguridad.
             */
            if (!destino.startsWith(directorio)) {
                throw new RuntimeException("Ruta de archivo no permitida");
            }

            /*
             * Guardar físicamente la imagen.
             */
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            /*
             * En PostgreSQL NO guardamos
             * la ruta absoluta de Windows.
             *
             * Guardamos solamente:
             * home/banner/archivo.jpg
             * o
             * home/colecciones/archivo.jfif
             */
            return rutaRelativa;

        } catch (IOException e) {
            throw new RuntimeException("Error al guardar la imagen", e);
        }
    }

    // =========================================================
    // ELIMINAR IMAGEN
    // =========================================================
    public void eliminar(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) {
            return;
        }

        // Producción: eliminar de Supabase Storage.
        if ("supabase".equalsIgnoreCase(storageProvider)) {
            supabaseStorageService.eliminar("productos/" + rutaRelativa);
            return;
        }

        if (!"local".equalsIgnoreCase(storageProvider)) {
            throw new IllegalStateException("Proveedor de almacenamiento no válido");
        }

        try {
            Path directorioBase = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path archivo = directorioBase.resolve(rutaRelativa).normalize();

            /*
             * Evitar eliminar archivos que
             * estén fuera del directorio
             * configurado.
             */
            if (!archivo.startsWith(directorioBase)) {
                throw new RuntimeException("Ruta de archivo no permitida");
            }

            Files.deleteIfExists(archivo);

        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar la imagen", e);
        }
    }

    // =========================================================
    // VALIDAR ARCHIVO
    // =========================================================
    private void validarArchivo(MultipartFile archivo) {

        /*
         * Debe existir un archivo.
         */
        if (archivo == null || archivo.isEmpty()) {
            throw new RuntimeException("Debe seleccionar una imagen");
        }

        /*
         * Validar tamaño.
         */
        if (archivo.getSize() > TAMANO_MAXIMO) {
            throw new RuntimeException("La imagen no puede superar los 5 MB");
        }

        /*
         * Validar extensión.
         * Admitimos:
         * JPG, JPEG, JFIF, PNG, WEBP.
         */
        String extension = obtenerExtension(archivo.getOriginalFilename());

        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new RuntimeException("Solo se permiten imágenes JPG, JPEG, JFIF, PNG o WEBP");
        }

        /*
         * Validar MIME enviado por el navegador.
         * Un archivo .jfif normalmente utiliza:
         * image/jpeg.
         */
        String contentType = archivo.getContentType();

        if (contentType == null || contentType.isBlank()) {
            throw new RuntimeException("No se pudo identificar el tipo de imagen");
        }

        contentType = contentType.toLowerCase(Locale.ROOT).trim();

        if (!TIPOS_PERMITIDOS.contains(contentType)) {
            throw new RuntimeException("Tipo de imagen no permitido. Use JPG, JPEG, JFIF, PNG o WEBP");
        }

        /*
         * Comprobación adicional:
         * JPG, JPEG y JFIF deben llegar
         * como image/jpeg o image/jpg.
         */
        if ((extension.equals("jpg") || extension.equals("jpeg") || extension.equals("jfif"))
                && !contentType.equals("image/jpeg") && !contentType.equals("image/jpg")) {
            throw new RuntimeException("El archivo JPG, JPEG o JFIF no tiene un tipo de imagen válido");
        }

        /*
         * PNG debe coincidir con image/png.
         */
        if (extension.equals("png") && !contentType.equals("image/png")) {
            throw new RuntimeException("El archivo PNG no tiene un tipo de imagen válido");
        }

        /*
         * WEBP debe coincidir con image/webp.
         */
        if (extension.equals("webp") && !contentType.equals("image/webp")) {
            throw new RuntimeException("El archivo WEBP no tiene un tipo de imagen válido");
        }
    }

    // =========================================================
    // OBTENER EXTENSIÓN
    // =========================================================
    private String obtenerExtension(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new RuntimeException("El archivo no tiene nombre");
        }

        int posicionPunto = nombre.lastIndexOf('.');

        if (posicionPunto < 0 || posicionPunto == nombre.length() - 1) {
            throw new RuntimeException("El archivo no tiene una extensión válida");
        }

        return nombre.substring(posicionPunto + 1).toLowerCase(Locale.ROOT).trim();
    }
}
