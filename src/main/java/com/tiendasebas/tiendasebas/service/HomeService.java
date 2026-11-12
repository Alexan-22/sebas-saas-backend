
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.HomeBanner;
import com.tiendasebas.tiendasebas.entity.HomeColeccion;
import com.tiendasebas.tiendasebas.repository.HomeBannerRepository;
import com.tiendasebas.tiendasebas.repository.HomeColeccionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class HomeService {

    private final HomeBannerRepository banners;
    private final HomeColeccionRepository colecciones;

    public HomeService(
            HomeBannerRepository banners,
            HomeColeccionRepository colecciones
    ) {
        this.banners = banners;
        this.colecciones = colecciones;
    }

    private Long tiendaActual() {
        Long tiendaId = TenantContext.getCurrentTenant();

        if (tiendaId == null || tiendaId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tienda no identificada"
            );
        }

        return tiendaId;
    }

    private ResponseStatusException noEncontrado(String recurso) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                recurso + " no encontrado"
        );
    }

    private void validarDatos(Object datos) {
        if (datos == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los datos son obligatorios"
            );
        }
    }

    // Obligatorio SOLO para colecciones.
    private String validarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El titulo es obligatorio"
            );
        }

        return titulo.trim();
    }

    // Para banners: permite null o cadena vacia.
    private String textoOpcional(String texto) {
        return texto == null ? null : texto.trim();
    }

    private Integer normalizarOrden(Integer orden) {
        return orden == null || orden < 1 ? 1 : orden;
    }

    private Boolean normalizarEstado(Boolean estado) {
        return estado == null ? true : estado;
    }

    // =====================================================
    // BANNERS PUBLICOS
    // =====================================================

    @Transactional(readOnly = true)
    public List<HomeBanner> listarBannersPublicos() {
        return banners.findByEstadoTrueAndTiendaIdOrderByOrdenAsc(
                tiendaActual()
        );
    }

    @Transactional(readOnly = true)
    public HomeBanner obtenerBannerPublico() {
        return listarBannersPublicos()
                .stream()
                .findFirst()
                .orElse(null);
    }

    // =====================================================
    // BANNERS ADMINISTRATIVOS
    // =====================================================

    @Transactional(readOnly = true)
    public List<HomeBanner> listarBannersAdmin() {
        return banners.findByTiendaIdOrderByOrdenAsc(
                tiendaActual()
        );
    }

    @Transactional(readOnly = true)
    public HomeBanner obtenerBannerAdmin() {
        return listarBannersAdmin()
                .stream()
                .findFirst()
                .orElseThrow(() -> noEncontrado("Banner"));
    }

    @Transactional(readOnly = true)
    public HomeBanner buscarBanner(Integer idBanner) {
        Long tiendaId = tiendaActual();

        HomeBanner banner = banners.findById(idBanner)
                .orElseThrow(() -> noEncontrado("Banner"));

        if (!Objects.equals(banner.getTiendaId(), tiendaId)) {
            throw noEncontrado("Banner");
        }

        return banner;
    }

    @Transactional
    public HomeBanner crearBanner(HomeBanner datos) {
        validarDatos(datos);

        HomeBanner banner = new HomeBanner();

        banner.setTiendaId(tiendaActual());

        banner.setTitulo(
                datos.getTitulo() == null
                        ? ""
                        : datos.getTitulo().trim()
        );
        banner.setEtiqueta(textoOpcional(datos.getEtiqueta()));
        banner.setDescripcion(textoOpcional(datos.getDescripcion()));

        // Los banners nuevos ya no tienen boton superpuesto.
        banner.setTextoBoton(null);
        banner.setEnlaceBoton(null);

        banner.setOrden(normalizarOrden(datos.getOrden()));
        banner.setEstado(normalizarEstado(datos.getEstado()));
        banner.setImagen(null);

        return banners.save(banner);
    }

    @Transactional
    public HomeBanner actualizarBanner(
            Integer idBanner,
            HomeBanner datos
    ) {
        validarDatos(datos);

        HomeBanner banner = buscarBanner(idBanner);

        banner.setTitulo(textoOpcional(datos.getTitulo()));
        banner.setEtiqueta(textoOpcional(datos.getEtiqueta()));
        banner.setDescripcion(textoOpcional(datos.getDescripcion()));

        // El cliente no puede reactivar los botones antiguos.
        banner.setTextoBoton(null);
        banner.setEnlaceBoton(null);

        banner.setOrden(normalizarOrden(datos.getOrden()));
        banner.setEstado(normalizarEstado(datos.getEstado()));

        return banners.save(banner);
    }

    @Transactional
    public HomeBanner actualizarImagenBanner(
            Integer idBanner,
            String nombreImagen
    ) {
        HomeBanner banner = buscarBanner(idBanner);
        banner.setImagen(nombreImagen);
        return banners.save(banner);
    }

    @Transactional
    public HomeBanner eliminarBanner(Integer idBanner) {
        HomeBanner banner = buscarBanner(idBanner);
        banners.delete(banner);
        return banner;
    }

    // =====================================================
    // COLECCIONES PUBLICAS
    // =====================================================

    @Transactional(readOnly = true)
    public List<HomeColeccion> listarColeccionesPublicas() {
        return colecciones.findByEstadoTrueAndTiendaIdOrderByOrdenAsc(
                tiendaActual()
        );
    }

    // =====================================================
    // COLECCIONES ADMINISTRATIVAS
    // =====================================================

    @Transactional(readOnly = true)
    public List<HomeColeccion> listarColeccionesAdmin() {
        return colecciones.findByTiendaIdOrderByOrdenAsc(
                tiendaActual()
        );
    }

    @Transactional(readOnly = true)
    public HomeColeccion buscarColeccion(Integer idColeccion) {
        Long tiendaId = tiendaActual();

        HomeColeccion coleccion = colecciones.findById(idColeccion)
                .orElseThrow(() -> noEncontrado("Coleccion"));

        if (!Objects.equals(coleccion.getTiendaId(), tiendaId)) {
            throw noEncontrado("Coleccion");
        }

        return coleccion;
    }

    @Transactional
    public HomeColeccion crearColeccion(HomeColeccion datos) {
        validarDatos(datos);

        HomeColeccion coleccion = new HomeColeccion();

        coleccion.setTiendaId(tiendaActual());
        coleccion.setTitulo(textoOpcional(datos.getTitulo()));
        coleccion.setEtiqueta(datos.getEtiqueta());
        coleccion.setTextoBoton(datos.getTextoBoton());
        coleccion.setEnlace(datos.getEnlace());
        coleccion.setOrden(normalizarOrden(datos.getOrden()));
        coleccion.setEstado(normalizarEstado(datos.getEstado()));
        coleccion.setImagen(null);

        return colecciones.save(coleccion);
    }

    @Transactional
    public HomeColeccion actualizarColeccion(
            Integer idColeccion,
            HomeColeccion datos
    ) {
        validarDatos(datos);

        HomeColeccion coleccion = buscarColeccion(idColeccion);

        coleccion.setTitulo(textoOpcional(datos.getTitulo()));
        coleccion.setEtiqueta(datos.getEtiqueta());
        coleccion.setTextoBoton(datos.getTextoBoton());
        coleccion.setEnlace(datos.getEnlace());
        coleccion.setOrden(normalizarOrden(datos.getOrden()));
        coleccion.setEstado(normalizarEstado(datos.getEstado()));

        return colecciones.save(coleccion);
    }


    @Transactional
    public HomeColeccion actualizarImagenColeccion(
            Integer idColeccion,
            String nombreImagen
    ) {
        HomeColeccion coleccion = buscarColeccion(idColeccion);
        coleccion.setImagen(nombreImagen);
        return colecciones.save(coleccion);
    }

    @Transactional
    public HomeColeccion eliminarColeccion(Integer idColeccion) {
        HomeColeccion coleccion = buscarColeccion(idColeccion);
        colecciones.delete(coleccion);
        return coleccion;
    }
}
