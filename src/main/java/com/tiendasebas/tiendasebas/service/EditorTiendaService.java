package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.service.CabeceraTiendaService.CabeceraConfig;
import com.tiendasebas.tiendasebas.service.FooterTiendaService.FooterConfig;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Objects;

@Service
public class EditorTiendaService {
    public static final List<String> SECCIONES_HOME =
            List.of("BANNERS", "CATEGORIAS", "COLECCIONES", "PRODUCTOS", "CIERRE", "BENEFICIOS");

    public record Banner(int id, int orden, boolean visible, String escritorio, String movil, String titulo) {}
    public record Coleccion(int id, int orden, boolean visible, String titulo, String imagen,
                            String etiqueta, String textoBoton, String enlace) {}
    public record Portada(List<Banner> banners, List<Coleccion> colecciones) {
        public Portada {
            if (banners == null || colecciones == null || banners.stream().anyMatch(Objects::isNull)
                    || colecciones.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Indica banners y colecciones válidos");
            }
            banners = List.copyOf(banners);
            colecciones = List.copyOf(colecciones);
        }
    }

    public enum DestinoImagen { ESCRITORIO, MOVIL, COLECCION }

    public record Tema(String primario, String secundario, String tipografia) {}

    public record Contenido(CabeceraConfig cabecera, FooterConfig footer, List<String> seccionesHome,
                            Portada portada, Tema tema) {
        public Contenido {
            seccionesHome = seccionesHome == null ? SECCIONES_HOME : List.copyOf(seccionesHome);
        }
    }
    public record Borrador(long revision, Contenido contenido) {}
    public record Version(long version, OffsetDateTime publicadoEn, String autor) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CabeceraTiendaService cabecera;
    private final FooterTiendaService footer;
    private final HomeService home;

    public EditorTiendaService(JdbcTemplate jdbc, ObjectMapper json,
                              CabeceraTiendaService cabecera, FooterTiendaService footer, HomeService home) {
        this.jdbc = jdbc;
        this.json = json;
        this.cabecera = cabecera;
        this.footer = footer;
        this.home = home;
    }

    @Transactional(readOnly = true)
    public Borrador obtener() {
        return obtener(tienda());
    }

    @Transactional
    public Borrador guardarCabecera(long revision, CabeceraConfig datos) {
        long tienda = bloquearTienda();
        Borrador actual = comprobarRevision(tienda, revision);
        return guardar(tienda, new Contenido(cabecera.validar(datos), actual.contenido().footer(), actual.contenido().seccionesHome(), actual.contenido().portada(), actual.contenido().tema()));
    }

    @Transactional
    public Borrador guardarFooter(long revision, FooterConfig datos) {
        long tienda = bloquearTienda();
        Borrador actual = comprobarRevision(tienda, revision);
        return guardar(tienda, new Contenido(actual.contenido().cabecera(), footer.validar(datos), actual.contenido().seccionesHome(), actual.contenido().portada(), actual.contenido().tema()));
    }

    @Transactional
    public Borrador guardarHome(long revision, List<String> secciones) {
        long tienda = bloquearTienda();
        Contenido actual = comprobarRevision(tienda, revision).contenido();
        if (secciones == null || secciones.size() != SECCIONES_HOME.size()
                || !new HashSet<>(secciones).containsAll(SECCIONES_HOME)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Orden de secciones inválido");
        }
        return guardar(tienda, new Contenido(actual.cabecera(), actual.footer(), secciones, actual.portada(), actual.tema()));
    }

    @Transactional(readOnly = true)
    public Portada recursosHome() {
        return new Portada(
                home.listarBannersAdmin().stream().map(b -> new Banner(
                        b.getIdBanner(), b.getOrden(), b.getEstado(), b.getImagen(), null, b.getTitulo())).toList(),
                home.listarColeccionesAdmin().stream().map(c -> new Coleccion(
                        c.getIdColeccion(), c.getOrden(), c.getEstado(), c.getTitulo(), c.getImagen(), c.getEtiqueta(), c.getTextoBoton(), c.getEnlace())).toList());
    }

    @Transactional
    public Borrador guardarPortada(long revision, Portada portada) {
        long tienda = bloquearTienda();
        Contenido actual = comprobarRevision(tienda, revision).contenido();
        if (portada == null || portada.banners().size() > 100 || portada.colecciones().size() > 100) {
            throw portadaInvalida();
        }

        var imagenesBanner = new HashSet<String>();
        home.listarBannersAdmin().forEach(b -> imagenesBanner.add(b.getImagen()));
        var ids = new HashSet<Integer>();
        List<Banner> banners = new ArrayList<>();
        for (Banner banner : portada.banners()) {
            var origen = home.buscarBanner(banner.id());
            if (!ids.add(banner.id()) || banner.orden() < 1
                    || !Objects.equals(origen.getImagen(), banner.escritorio()) && !imagenGuardada(actual.portada(), banner, false)
                    || (banner.movil() != null && !imagenesBanner.contains(banner.movil())
                        && !imagenGuardada(actual.portada(), banner, true))) {
                throw portadaInvalida();
            }
            banners.add(new Banner(banner.id(), banner.orden(), banner.visible(),
                    banner.escritorio(), banner.movil(), origen.getTitulo()));
        }

        ids.clear();
        List<Coleccion> colecciones = new ArrayList<>();
        for (Coleccion coleccion : portada.colecciones()) {
            var origen = home.buscarColeccion(coleccion.id());
            if (!ids.add(coleccion.id()) || coleccion.orden() < 1
                    || (coleccion.titulo() != null && coleccion.titulo().length() > 100)
                    || (!Objects.equals(origen.getImagen(), coleccion.imagen())
                        && (actual.portada() == null || actual.portada().colecciones().stream().noneMatch(c ->
                            c.id() == coleccion.id() && Objects.equals(c.imagen(), coleccion.imagen()))))) {
                throw portadaInvalida();
            }
            colecciones.add(new Coleccion(coleccion.id(), coleccion.orden(), coleccion.visible(),
                    coleccion.titulo(), coleccion.imagen(), origen.getEtiqueta(), origen.getTextoBoton(), origen.getEnlace()));
        }
        return guardar(tienda, new Contenido(actual.cabecera(), actual.footer(), actual.seccionesHome(),
                new Portada(banners, colecciones), actual.tema()));
    }

    private boolean imagenGuardada(Portada portada, Banner banner, boolean movil) {
        return portada != null && portada.banners().stream().anyMatch(b -> b.id() == banner.id()
                && (movil ? Objects.equals(b.movil(), banner.movil())
                          : Objects.equals(b.escritorio(), banner.escritorio())));
    }

    @Transactional
    public Borrador subirImagen(long revision, DestinoImagen destino, int id, MultipartFile archivo,
                                HomeImagenService imagenes) {
        long tienda = bloquearTienda();
        Contenido actual = comprobarRevision(tienda, revision).contenido();
        if (actual.portada() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Guarda la portada antes de subir imágenes");
        }

        List<Banner> banners = new ArrayList<>(actual.portada().banners());
        List<Coleccion> colecciones = new ArrayList<>(actual.portada().colecciones());
        if (destino == DestinoImagen.COLECCION) {
            home.buscarColeccion(id);
            Coleccion c = colecciones.stream().filter(item -> item.id() == id).findFirst()
                    .orElseThrow(this::portadaInvalida);
            String imagen = imagenes.guardarParaEditor(archivo, tienda);
            colecciones.set(colecciones.indexOf(c), new Coleccion(c.id(), c.orden(), c.visible(), c.titulo(),
                    imagen, c.etiqueta(), c.textoBoton(), c.enlace()));
        } else {
            home.buscarBanner(id);
            Banner b = banners.stream().filter(item -> item.id() == id).findFirst()
                    .orElseThrow(this::portadaInvalida);
            String imagen = imagenes.guardarParaEditor(archivo, tienda);
            banners.set(banners.indexOf(b), new Banner(b.id(), b.orden(), b.visible(),
                    destino == DestinoImagen.ESCRITORIO ? imagen : b.escritorio(),
                    destino == DestinoImagen.MOVIL ? imagen : b.movil(), b.titulo()));
        }
        return guardar(tienda, new Contenido(actual.cabecera(), actual.footer(), actual.seccionesHome(),
                new Portada(banners, colecciones), actual.tema()));
    }

    @Transactional
    public Borrador guardarTema(long revision, Tema tema) {
        long tienda = bloquearTienda();
        Contenido actual = comprobarRevision(tienda, revision).contenido();
        if (tema == null || !colorValido(tema.primario()) || !colorValido(tema.secundario())
                || !List.of("SANS", "SERIF", "MONO").contains(Objects.toString(tema.tipografia(), ""))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tema visual inválido");
        }

        return guardar(tienda, new Contenido(actual.cabecera(), actual.footer(),
                actual.seccionesHome(), actual.portada(), tema));
    }

    private boolean colorValido(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{6}");
    }

    private ResponseStatusException portadaInvalida() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Portada inválida");
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Contenido disenoPublicado() {
        return publicado();
    }

    @Transactional
    public Borrador publicar(long revision, String autor) {
        long tienda = bloquearTienda();
        Borrador actual = comprobarRevision(tienda, revision);
        if (revision == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Guarda un borrador antes de publicar");
        }

        long ultima = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version), 0) FROM public.saas_editor_versiones WHERE tienda_id = ?
                """, Long.class, tienda);
        if (ultima == 0) {
            guardarVersion(tienda, 0, publicado(), autor);
        }

        Contenido contenido = actual.contenido();
        Contenido publicado = new Contenido(cabecera.guardar(contenido.cabecera()),
                footer.guardar(contenido.footer()), contenido.seccionesHome(), contenido.portada(), contenido.tema());
        guardarVersion(tienda, ultima + 1, publicado, autor);
        jdbc.update("DELETE FROM public.saas_editor_borradores WHERE tienda_id = ?", tienda);
        return new Borrador(0, publicado);
    }

    @Transactional(readOnly = true)
    public List<Version> historial() {
        return jdbc.query("""
                SELECT version, publicado_en, autor FROM public.saas_editor_versiones
                WHERE tienda_id = ? ORDER BY version DESC
                """, (rs, i) -> new Version(rs.getLong("version"),
                rs.getObject("publicado_en", OffsetDateTime.class), rs.getString("autor")), tienda());
    }

    @Transactional
    public Borrador restaurar(long version, long revision) {
        long tienda = bloquearTienda();
        comprobarRevision(tienda, revision);
        Contenido anterior = jdbc.query("""
                SELECT contenido FROM public.saas_editor_versiones WHERE tienda_id = ? AND version = ?
                """, (rs, i) -> json.readValue(rs.getString(1), Contenido.class), tienda, version)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return guardar(tienda, anterior);
    }

    private Borrador obtener(long tienda) {
        return jdbc.query("""
                SELECT revision, contenido FROM public.saas_editor_borradores WHERE tienda_id = ?
                """, (rs, i) -> new Borrador(rs.getLong(1), json.readValue(rs.getString(2), Contenido.class)), tienda)
                .stream().findFirst().orElseGet(() -> new Borrador(0, publicado()));
    }

    private Contenido publicado() {
        Contenido version = jdbc.query("""
                SELECT contenido FROM public.saas_editor_versiones
                WHERE tienda_id = ? AND version > 0 ORDER BY version DESC LIMIT 1
                """, (rs, i) -> json.readValue(rs.getString(1), Contenido.class), tienda())
                .stream().findFirst().orElse(null);
        return new Contenido(cabecera.obtener(), footer.obtener(),
                version == null ? SECCIONES_HOME : version.seccionesHome(),
                version == null ? null : version.portada(), version == null ? null : version.tema());
    }

    private Borrador comprobarRevision(long tienda, long revision) {
        Borrador actual = obtener(tienda);
        if (actual.revision() != revision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El borrador cambió. Vuelve a cargarlo");
        }
        return actual;
    }

    private Borrador guardar(long tienda, Contenido contenido) {
        long revision = jdbc.queryForObject("SELECT nextval('public.saas_editor_revision_seq')", Long.class);
        jdbc.update("""
                INSERT INTO public.saas_editor_borradores (tienda_id, revision, contenido)
                VALUES (?, ?, CAST(? AS jsonb))
                ON CONFLICT (tienda_id) DO UPDATE SET revision = EXCLUDED.revision, contenido = EXCLUDED.contenido
                """, tienda, revision, json.writeValueAsString(contenido));
        return new Borrador(revision, contenido);
    }

    private void guardarVersion(long tienda, long version, Contenido contenido, String autor) {
        jdbc.update("""
                INSERT INTO public.saas_editor_versiones (tienda_id, version, contenido, autor)
                VALUES (?, ?, CAST(? AS jsonb), ?)
                """, tienda, version, json.writeValueAsString(contenido), autor);
    }

    private long bloquearTienda() {
        return jdbc.queryForObject("SELECT id FROM public.tiendas WHERE id = ? FOR UPDATE", Long.class, tienda());
    }

    private long tienda() {
        Long tienda = TenantContext.getCurrentTenant();
        if (tienda == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return tienda;
    }
}
