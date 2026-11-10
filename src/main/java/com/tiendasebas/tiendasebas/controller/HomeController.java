package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.HomeBanner;
import com.tiendasebas.tiendasebas.entity.HomeColeccion;
import com.tiendasebas.tiendasebas.service.HomeImagenService;
import com.tiendasebas.tiendasebas.service.HomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;
    private final HomeImagenService homeImagenService;

    public HomeController(HomeService homeService, HomeImagenService homeImagenService) {
        this.homeService = homeService;
        this.homeImagenService = homeImagenService;
    }

    // =========================================================
    // PÚBLICO - LISTAR BANNERS ACTIVOS
    // =========================================================
    @GetMapping("/public/banners")
    public ResponseEntity<List<HomeBanner>> listarBannersPublicos() {
        return ResponseEntity.ok(homeService.listarBannersPublicos());
    }

    // =========================================================
    // PÚBLICO - BANNER ANTIGUO
    // Compatibilidad temporal con Angular actual
    // =========================================================
    @GetMapping("/public/banner")
    public ResponseEntity<HomeBanner> obtenerBannerPublico() {
        HomeBanner banner = homeService.obtenerBannerPublico();
        if (banner == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(banner);
    }

    // =========================================================
    // PÚBLICO - COLECCIONES ACTIVAS
    // =========================================================
    @GetMapping("/public/colecciones")
    public ResponseEntity<List<HomeColeccion>> listarColeccionesPublicas() {
        return ResponseEntity.ok(homeService.listarColeccionesPublicas());
    }

    // =========================================================
    // ADMIN - LISTAR BANNERS
    // =========================================================
    @GetMapping("/admin/banners")
    public ResponseEntity<List<HomeBanner>> listarBannersAdmin() {
        return ResponseEntity.ok(homeService.listarBannersAdmin());
    }

    // =========================================================
    // ADMIN - OBTENER UN BANNER
    // =========================================================
    @GetMapping("/admin/banners/{idBanner}")
    public ResponseEntity<HomeBanner> buscarBanner(@PathVariable Integer idBanner) {
        return ResponseEntity.ok(homeService.buscarBanner(idBanner));
    }

    // =========================================================
    // ADMIN - BANNER ANTIGUO
    // Compatibilidad temporal
    // =========================================================
    @GetMapping("/admin/banner")
    public ResponseEntity<HomeBanner> obtenerBannerAdmin() {
        return ResponseEntity.ok(homeService.obtenerBannerAdmin());
    }

    // =========================================================
    // ADMIN - CREAR BANNER
    // =========================================================
    @PostMapping("/admin/banners")
    public ResponseEntity<HomeBanner> crearBanner(@RequestBody HomeBanner banner) {
        return ResponseEntity.ok(homeService.crearBanner(banner));
    }

    // =========================================================
    // ADMIN - ACTUALIZAR BANNER
    // NUEVO ENDPOINT
    // =========================================================
    @PutMapping("/admin/banners/{idBanner}")
    public ResponseEntity<HomeBanner> actualizarBannerNuevo(@PathVariable Integer idBanner, @RequestBody HomeBanner banner) {
        return ResponseEntity.ok(homeService.actualizarBanner(idBanner, banner));
    }

    // =========================================================
    // ADMIN - ACTUALIZAR BANNER
    // ENDPOINT ANTIGUO
    // Compatibilidad con Angular actual
    // =========================================================
    @PutMapping("/admin/banner/{idBanner}")
    public ResponseEntity<HomeBanner> actualizarBannerAntiguo(@PathVariable Integer idBanner, @RequestBody HomeBanner banner) {
        return ResponseEntity.ok(homeService.actualizarBanner(idBanner, banner));
    }

    // =========================================================
    // ADMIN - SUBIR / CAMBIAR IMAGEN BANNER
    // NUEVO ENDPOINT
    // =========================================================
    @PostMapping(value = "/admin/banners/{idBanner}/imagen", consumes = "multipart/form-data")
    public ResponseEntity<HomeBanner> subirImagenBannerNueva(@PathVariable Integer idBanner, @RequestParam("imagen") MultipartFile imagen) {
        return procesarImagenBanner(idBanner, imagen);
    }

    // =========================================================
    // ADMIN - SUBIR / CAMBIAR IMAGEN BANNER
    // ENDPOINT ANTIGUO
    // Compatibilidad con Angular actual
    // =========================================================
    @PostMapping(value = "/admin/banner/{idBanner}/imagen", consumes = "multipart/form-data")
    public ResponseEntity<HomeBanner> subirImagenBannerAntigua(@PathVariable Integer idBanner, @RequestParam("imagen") MultipartFile imagen) {
        return procesarImagenBanner(idBanner, imagen);
    }

    // =========================================================
    // ADMIN - ELIMINAR BANNER
    // =========================================================
    @DeleteMapping("/admin/banners/{idBanner}")
    public ResponseEntity<Void> eliminarBanner(@PathVariable Integer idBanner) {
        HomeBanner banner = homeService.eliminarBanner(idBanner);
        eliminarImagenSiExiste(banner.getImagen());
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // ADMIN - LISTAR COLECCIONES
    // =========================================================
    @GetMapping("/admin/colecciones")
    public ResponseEntity<List<HomeColeccion>> listarColeccionesAdmin() {
        return ResponseEntity.ok(homeService.listarColeccionesAdmin());
    }

    // =========================================================
    // ADMIN - BUSCAR COLECCIÓN
    // =========================================================
    @GetMapping("/admin/colecciones/{idColeccion}")
    public ResponseEntity<HomeColeccion> buscarColeccion(@PathVariable Integer idColeccion) {
        return ResponseEntity.ok(homeService.buscarColeccion(idColeccion));
    }

    // =========================================================
    // ADMIN - CREAR COLECCIÓN
    // =========================================================
    @PostMapping("/admin/colecciones")
    public ResponseEntity<HomeColeccion> crearColeccion(@RequestBody HomeColeccion coleccion) {
        return ResponseEntity.ok(homeService.crearColeccion(coleccion));
    }

    // =========================================================
    // ADMIN - ACTUALIZAR COLECCIÓN
    // =========================================================
    @PutMapping("/admin/colecciones/{idColeccion}")
    public ResponseEntity<HomeColeccion> actualizarColeccion(@PathVariable Integer idColeccion, @RequestBody HomeColeccion coleccion) {
        return ResponseEntity.ok(homeService.actualizarColeccion(idColeccion, coleccion));
    }

    // =========================================================
    // ADMIN - SUBIR / CAMBIAR IMAGEN COLECCIÓN
    // =========================================================
    @PostMapping(value = "/admin/colecciones/{idColeccion}/imagen", consumes = "multipart/form-data")
    public ResponseEntity<HomeColeccion> subirImagenColeccion(@PathVariable Integer idColeccion, @RequestParam("imagen") MultipartFile imagen) {

        /*
         * Recuperamos la colección antes de cambiar
         * la imagen para poder eliminar la anterior.
         */
        HomeColeccion coleccionActual = homeService.buscarColeccion(idColeccion);
        String imagenAnterior = coleccionActual.getImagen();

        /*
         * Guardamos físicamente la nueva imagen.
         */
        String nuevaImagen = homeImagenService.guardarColeccion(imagen);

        try {
            /*
             * Actualizamos PostgreSQL.
             */
            HomeColeccion coleccionActualizada = homeService.actualizarImagenColeccion(idColeccion, nuevaImagen);

            /*
             * Solamente después de actualizar
             * correctamente la base de datos
             * eliminamos la imagen anterior.
             */
            if (imagenAnterior != null && !imagenAnterior.isBlank() && !imagenAnterior.equals(nuevaImagen)) {
                homeImagenService.eliminar(imagenAnterior);
            }

            return ResponseEntity.ok(coleccionActualizada);

        } catch (RuntimeException e) {
            /*
             * Si PostgreSQL falla después de guardar
             * físicamente la imagen nueva, eliminamos
             * el archivo recién creado para no dejar
             * archivos huérfanos.
             */
            homeImagenService.eliminar(nuevaImagen);
            throw e;
        }
    }

    // =========================================================
    // ADMIN - ELIMINAR COLECCIÓN
    // =========================================================
    @DeleteMapping("/admin/colecciones/{idColeccion}")
    public ResponseEntity<Void> eliminarColeccion(@PathVariable Integer idColeccion) {
        HomeColeccion coleccion = homeService.eliminarColeccion(idColeccion);
        eliminarImagenSiExiste(coleccion.getImagen());
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // MÉTODO INTERNO - PROCESAR IMAGEN BANNER
    // =========================================================
    private ResponseEntity<HomeBanner> procesarImagenBanner(Integer idBanner, MultipartFile imagen) {

        /*
         * IMPORTANTE:
         *
         * Ahora buscamos exactamente el banner
         * indicado por idBanner.
         *
         * Ya no utilizamos obtenerBannerAdmin(),
         * porque ahora pueden existir muchos banners.
         */
        HomeBanner bannerActual = homeService.buscarBanner(idBanner);
        String imagenAnterior = bannerActual.getImagen();

        /*
         * Guardamos físicamente la nueva imagen.
         */
        String nuevaImagen = homeImagenService.guardarBanner(imagen);

        try {
            /*
             * Actualizamos PostgreSQL.
             */
            HomeBanner bannerActualizado = homeService.actualizarImagenBanner(idBanner, nuevaImagen);

            /*
             * Cuando PostgreSQL ya fue actualizado,
             * eliminamos la imagen anterior.
             */
            if (imagenAnterior != null && !imagenAnterior.isBlank() && !imagenAnterior.equals(nuevaImagen)) {
                homeImagenService.eliminar(imagenAnterior);
            }

            return ResponseEntity.ok(bannerActualizado);

        } catch (RuntimeException e) {
            /*
             * Si ocurre un error actualizando la BD,
             * eliminamos la nueva imagen para evitar
             * archivos huérfanos.
             */
            homeImagenService.eliminar(nuevaImagen);
            throw e;
        }
    }

    // =========================================================
    // MÉTODO INTERNO - ELIMINAR IMAGEN
    // =========================================================
    private void eliminarImagenSiExiste(String imagen) {
        if (imagen == null || imagen.isBlank()) return;
        homeImagenService.eliminar(imagen);
    }
}
