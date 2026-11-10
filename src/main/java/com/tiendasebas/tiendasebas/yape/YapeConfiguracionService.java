package com.tiendasebas.tiendasebas.yape;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class YapeConfiguracionService {

    private static final String QR_URL = "/api/yape/qr";
    private final JdbcTemplate jdbc;

    public YapeConfiguracionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private long tiendaActual() {
        Long id = TenantContext.getCurrentTenant();
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tienda no resuelta");
        }
        return id;
    }

    public record ImagenQr(byte[] datos) {}

    private boolean tieneQr(long tenant) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM public.yape_qr_imagen WHERE tienda_id = ?)",
                Boolean.class, tenant);
        return Boolean.TRUE.equals(existe);
    }

    @Transactional(readOnly = true)
    public YapeConfiguracion consultarAdmin() {
        long tenant = tiendaActual();
        boolean qr = tieneQr(tenant);
        return jdbc.query("""
                SELECT habilitado, numero_yape, titular_yape
                FROM public.yape_configuracion_tienda WHERE tienda_id = ?
                """, rs -> rs.next()
                    ? new YapeConfiguracion(rs.getBoolean("habilitado"),
                        rs.getString("numero_yape"), rs.getString("titular_yape"),
                        qr ? QR_URL : null)
                    : YapeConfiguracion.deshabilitada(), tenant);
    }

    /** Solo expone datos de cobro si la tienda está ACTIVA y Yape habilitado. */
    @Transactional(readOnly = true)
    public YapeConfiguracion consultarPublico() {
        long tenant = tiendaActual();
        return jdbc.query("""
                SELECT y.numero_yape, y.titular_yape
                FROM public.yape_configuracion_tienda y
                JOIN public.tiendas t ON t.id = y.tienda_id
                JOIN public.yape_qr_imagen q ON q.tienda_id = y.tienda_id
                WHERE y.tienda_id = ? AND t.estado = 'ACTIVA' AND y.habilitado = true
                """, rs -> rs.next()
                    ? new YapeConfiguracion(true, rs.getString("numero_yape"),
                        rs.getString("titular_yape"), QR_URL)
                    : YapeConfiguracion.deshabilitada(), tenant);
    }

    @Transactional
    public YapeConfiguracion actualizar(ActualizarYapeRequest request) {
        if (request == null || request.habilitado() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique habilitado");
        }
        // Evita inyección de URL externa: el servidor es quien define la dirección del QR.
        if (request.qrUrl() != null && !request.qrUrl().isBlank()
                && !QR_URL.equals(request.qrUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El QR debe subirse desde el panel; no se permiten URLs externas");
        }
        long tenant = tiendaActual();
        boolean habilitado = request.habilitado();
        String numero = limpio(request.numeroYape());
        String titular = limpio(request.titularYape());
        boolean existeQr = tieneQr(tenant);

        if (numero != null && !numero.matches("9[0-9]{8}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Número Yape inválido");
        }
        if (titular != null && titular.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titular demasiado largo");
        }
        if (habilitado && (numero == null || titular == null || !existeQr)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para habilitar Yape debes completar número, titular y subir el QR");
        }
        String url = existeQr ? QR_URL : null;
        jdbc.update("""
                INSERT INTO public.yape_configuracion_tienda
                    (tienda_id, habilitado, numero_yape, titular_yape, qr_url, actualizado_en)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (tienda_id) DO UPDATE SET
                    habilitado = EXCLUDED.habilitado,
                    numero_yape = EXCLUDED.numero_yape,
                    titular_yape = EXCLUDED.titular_yape,
                    qr_url = EXCLUDED.qr_url,
                    actualizado_en = now()
                """, tenant, habilitado, numero, titular, url);
        return new YapeConfiguracion(habilitado, numero, titular, url);
    }

    @Transactional
    public YapeConfiguracion cargarQr(byte[] datos) {
        long tenant = tiendaActual();
        YapeQrValidator.QrValidado qr;
        try {
            qr = YapeQrValidator.validar(datos);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        jdbc.update("""
                INSERT INTO public.yape_qr_imagen
                    (tienda_id, imagen, tipo_mime, tamano_bytes, sha256, actualizado_en)
                VALUES (?, ?, 'image/png', ?, ?, now())
                ON CONFLICT (tienda_id) DO UPDATE SET
                    imagen = EXCLUDED.imagen,
                    tipo_mime = EXCLUDED.tipo_mime,
                    tamano_bytes = EXCLUDED.tamano_bytes,
                    sha256 = EXCLUDED.sha256,
                    actualizado_en = now()
                """, tenant, qr.imagen(), qr.imagen().length, qr.sha256());
        // Crear configuración deshabilitada si la tienda aún no tiene registro.
        jdbc.update("""
                INSERT INTO public.yape_configuracion_tienda(tienda_id, habilitado, qr_url)
                VALUES (?, false, ?)
                ON CONFLICT (tienda_id) DO UPDATE SET
                    qr_url = EXCLUDED.qr_url, actualizado_en = now()
                """, tenant, QR_URL);
        return consultarAdmin();
    }

    @Transactional(readOnly = true)
    public ImagenQr imagenAdmin() {
        long tenant = tiendaActual();
        List<byte[]> datos = jdbc.query("""
                SELECT imagen FROM public.yape_qr_imagen WHERE tienda_id = ?
                """, (rs, row) -> rs.getBytes(1), tenant);
        if (datos.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR no configurado");
        return new ImagenQr(datos.getFirst());
    }

    @Transactional(readOnly = true)
    public ImagenQr imagenPublica() {
        long tenant = tiendaActual();
        List<byte[]> datos = jdbc.query("""
                SELECT q.imagen FROM public.yape_qr_imagen q
                JOIN public.yape_configuracion_tienda y ON y.tienda_id = q.tienda_id
                JOIN public.tiendas t ON t.id = q.tienda_id
                WHERE q.tienda_id = ? AND y.habilitado = true AND t.estado = 'ACTIVA'
                """, (rs, row) -> rs.getBytes(1), tenant);
        if (datos.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR no disponible");
        return new ImagenQr(datos.getFirst());
    }

    private static String limpio(String valor) {
        if (valor == null) return null;
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }
}
