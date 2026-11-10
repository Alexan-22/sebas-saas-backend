package com.tiendasebas.tiendasebas.yape;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

/** Evidencia privada. Nunca aprueba por si sola un pago Yape. */
@Service
public class YapeComprobanteService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private final JdbcTemplate jdbc;

    public YapeComprobanteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ComprobanteInfo(int idVenta, String mimeTipo, int tamanoBytes,
                                  String sha256, Instant enviadoEn) {}
    public record Imagen(byte[] bytes, String mimeTipo) {}
    public record ContactoWhatsapp(String numeroWhatsapp, String url) {}
    private record Limpia(byte[] bytes, String mime) {}

    private static long tienda() {
        Long id = TenantContext.getCurrentTenant();
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tienda no identificada");
        return id;
    }

    private static ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado");
    }

    // Correo y tienda se obtienen del JWT y del TenantContext, nunca del formulario.
    private static final String ES_DUENO = """
        SELECT p.estado
        FROM public.yape_pagos_manuales p
        WHERE p.tienda_id = ? AND p.id_venta = ?
          AND p.usuario_id IN (
              SELECT u.id_usuario FROM public.usuarios u
              WHERE u.tienda_id = p.tienda_id AND u.correo = ? AND u.estado = true
          )
        """;

    @Transactional
    public ComprobanteInfo subir(int idVenta, String correo, MultipartFile archivo) {
        if (idVenta <= 0 || correo == null || correo.isBlank()) throw noEncontrado();
        // Comprobar y normalizar la imagen antes del bloqueo de la fila de pago.
        Limpia limpia = limpiarImagen(archivo);
        long tiendaId = tienda();
        List<String> estados = jdbc.query(ES_DUENO + " FOR UPDATE",
                (rs, i) -> rs.getString(1), tiendaId, idVenta, correo);
        if (estados.isEmpty()) throw noEncontrado();
        if (!"PENDIENTE".equals(estados.getFirst())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pago ya no esta pendiente");
        }
        String hash = sha256(limpia.bytes());
        // ON CONFLICT evita reemplazos; la evidencia enviada queda inmutable.
        int filas = jdbc.update("""
                INSERT INTO public.yape_comprobantes
                  (tienda_id, id_venta, usuario_id, imagen, mime_tipo, tamano_bytes, sha256)
                SELECT ?, ?, p.usuario_id, ?, ?, ?, ?
                FROM public.yape_pagos_manuales p
                WHERE p.tienda_id = ? AND p.id_venta = ? AND p.estado = 'PENDIENTE'
                ON CONFLICT (tienda_id, id_venta) DO NOTHING
                """, tiendaId, idVenta, limpia.bytes(), limpia.mime(),
                limpia.bytes().length, hash, tiendaId, idVenta);
        if (filas != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El pedido ya tiene un comprobante o cambio su estado");
        }
        return infoCliente(idVenta, correo);
    }

    @Transactional(readOnly = true)
    public ComprobanteInfo infoCliente(int idVenta, String correo) {
        long tiendaId = tienda();
        List<ComprobanteInfo> infos = jdbc.query("""
                SELECT c.id_venta, c.mime_tipo, c.tamano_bytes, c.sha256, c.enviado_en
                FROM public.yape_comprobantes c
                JOIN public.yape_pagos_manuales p
                  ON p.tienda_id=c.tienda_id AND p.id_venta=c.id_venta
                WHERE c.tienda_id=? AND c.id_venta=?
                  AND p.usuario_id IN (
                    SELECT u.id_usuario FROM public.usuarios u
                    WHERE u.tienda_id=p.tienda_id AND u.correo=? AND u.estado=true)
                """, (rs, i) -> mapear(rs), tiendaId, idVenta, correo);
        if (infos.isEmpty()) throw noEncontrado();
        return infos.getFirst();
    }

    @Transactional(readOnly = true)
    public ComprobanteInfo infoAdmin(int idVenta) {
        List<ComprobanteInfo> infos = jdbc.query("""
                SELECT id_venta, mime_tipo, tamano_bytes, sha256, enviado_en
                FROM public.yape_comprobantes
                WHERE tienda_id=? AND id_venta=?
                """, (rs, i) -> mapear(rs), tienda(), idVenta);
        if (infos.isEmpty()) throw noEncontrado();
        return infos.getFirst();
    }

    private static ComprobanteInfo mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp fecha = rs.getTimestamp("enviado_en");
        return new ComprobanteInfo(rs.getInt("id_venta"), rs.getString("mime_tipo"),
                rs.getInt("tamano_bytes"), rs.getString("sha256"),
                fecha == null ? null : fecha.toInstant());
    }

    @Transactional(readOnly = true)
    public Imagen imagenCliente(int idVenta, String correo) {
        long tiendaId = tienda();
        List<Imagen> imagenes = jdbc.query("""
                SELECT c.imagen,c.mime_tipo
                FROM public.yape_comprobantes c
                JOIN public.yape_pagos_manuales p
                  ON p.tienda_id=c.tienda_id AND p.id_venta=c.id_venta
                WHERE c.tienda_id=? AND c.id_venta=?
                  AND p.usuario_id IN (
                    SELECT u.id_usuario FROM public.usuarios u
                    WHERE u.tienda_id=p.tienda_id AND u.correo=? AND u.estado=true)
                """, (rs, i) -> new Imagen(rs.getBytes(1), rs.getString(2)),
                tiendaId, idVenta, correo);
        if (imagenes.isEmpty()) throw noEncontrado();
        return imagenes.getFirst();
    }

    @Transactional(readOnly = true)
    public Imagen imagenAdmin(int idVenta) {
        List<Imagen> imagenes = jdbc.query("""
                SELECT imagen,mime_tipo FROM public.yape_comprobantes
                WHERE tienda_id=? AND id_venta=?
                """, (rs, i) -> new Imagen(rs.getBytes(1), rs.getString(2)),
                tienda(), idVenta);
        if (imagenes.isEmpty()) throw noEncontrado();
        return imagenes.getFirst();
    }

    @Transactional(readOnly = true)
    public ContactoWhatsapp contacto(int idVenta, String correo) {
        long tiendaId = tienda();
        List<String> estados = jdbc.query(ES_DUENO, (rs, i) -> rs.getString(1),
                tiendaId, idVenta, correo);
        if (estados.isEmpty()) throw noEncontrado();
        List<String> numeros = jdbc.query("""
                SELECT COALESCE(NULLIF(trim(whatsapp),''), NULLIF(trim(telefono_soporte),''))
                FROM public.tiendas WHERE id=?
                """, (rs, i) -> rs.getString(1), tiendaId);
        String numero = numeros.isEmpty() ? null : normalizarTelefono(numeros.getFirst());
        if (numero == null) return new ContactoWhatsapp(null, null);
        String mensaje = "Hola, quiero enviar mi comprobante Yape del pedido #" + idVenta
                + ". Por favor, verifiquen el pago en su cuenta.";
        return new ContactoWhatsapp(numero,
                "https://wa.me/" + numero + "?text=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
    }

    private static String normalizarTelefono(String valor) {
        if (valor == null) return null;
        String digitos = valor.replaceAll("[^0-9]", "");
        if (digitos.matches("9[0-9]{8}")) return "51" + digitos;
        if (digitos.matches("519[0-9]{8}")) return digitos;
        return null;
    }

    private static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No disponible SHA-256", ex);
        }
    }

    private static Limpia limpiarImagen(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona una imagen PNG/JPG de hasta 5 MB");
        }
        try {
            byte[] original = archivo.getBytes();
            if (original.length == 0 || original.length > MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tamano de imagen invalido");
            }
            try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
                if (entrada == null) throw imagenInvalida();
                Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
                if (!lectores.hasNext()) throw imagenInvalida();
                ImageReader lector = lectores.next();
                try {
                    lector.setInput(entrada, true, true);
                    String formato = lector.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    boolean png = "png".equals(formato);
                    boolean jpg = "jpeg".equals(formato) || "jpg".equals(formato);
                    if (!png && !jpg) throw imagenInvalida();
                    int w = lector.getWidth(0), h = lector.getHeight(0);
                    if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dimensiones de imagen no permitidas");
                    }
                    BufferedImage imagen = lector.read(0);
                    if (imagen == null) throw imagenInvalida();
                    // Re-encodificar para eliminar metadatos EXIF y bytes externos a la imagen.
                    if (jpg && imagen.getType() != BufferedImage.TYPE_INT_RGB) {
                        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                        Graphics2D graficos = rgb.createGraphics();
                        try {
                            graficos.setColor(java.awt.Color.WHITE);
                            graficos.fillRect(0, 0, w, h);
                            graficos.drawImage(imagen, 0, 0, null);
                        } finally { graficos.dispose(); }
                        imagen = rgb;
                    }
                    ByteArrayOutputStream salida = new ByteArrayOutputStream();
                    boolean escrito = ImageIO.write(imagen, png ? "png" : "jpg", salida);
                    if (!escrito || salida.size() == 0 || salida.size() > MAX_BYTES) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen procesada demasiado grande");
                    }
                    return new Limpia(salida.toByteArray(), png ? "image/png" : "image/jpeg");
                } finally { lector.dispose(); }
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw imagenInvalida();
        }
    }

    private static ResponseStatusException imagenInvalida() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Archivo no compatible. Solo se aceptan imagenes PNG y JPG validas");
    }
}
