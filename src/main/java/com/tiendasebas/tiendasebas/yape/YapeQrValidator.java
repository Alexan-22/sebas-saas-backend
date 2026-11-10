package com.tiendasebas.tiendasebas.yape;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;

/** Normaliza PNG/JPG a PNG, elimina metadatos y limita tamaño/dimensiones. */
public final class YapeQrValidator {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MIN_DIMENSION = 100;
    private static final int MAX_DIMENSION = 2048;

    private YapeQrValidator() {}

    public record QrValidado(byte[] imagen, String sha256) {}

    public static QrValidado validar(byte[] datos) {
        if (datos == null || datos.length == 0 || datos.length > MAX_BYTES) {
            throw new IllegalArgumentException("El QR debe pesar entre 1 byte y 2 MB");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            if (input == null) throw new IllegalArgumentException("El archivo no es una imagen válida");
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(input);
            if (!lectores.hasNext()) throw new IllegalArgumentException("Solo se admiten imágenes PNG o JPG");
            ImageReader reader = lectores.next();
            try {
                reader.setInput(input, true, true);
                String formato = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!formato.equals("png") && !formato.equals("jpeg") && !formato.equals("jpg")) {
                    throw new IllegalArgumentException("Solo se admiten imágenes PNG o JPG");
                }
                int ancho = reader.getWidth(0);
                int alto = reader.getHeight(0);
                if (ancho < MIN_DIMENSION || alto < MIN_DIMENSION ||
                        ancho > MAX_DIMENSION || alto > MAX_DIMENSION) {
                    throw new IllegalArgumentException("El QR debe medir de 100 a 2048 píxeles por lado");
                }
                BufferedImage original = reader.read(0);
                if (original == null) throw new IllegalArgumentException("No se pudo abrir la imagen QR");
                BufferedImage limpia = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = limpia.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, ancho, alto);
                    g.drawImage(original, 0, 0, null);
                } finally {
                    g.dispose();
                }
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                if (!ImageIO.write(limpia, "png", salida)) {
                    throw new IllegalArgumentException("No se pudo normalizar la imagen");
                }
                byte[] png = salida.toByteArray();
                if (png.length == 0 || png.length > MAX_BYTES) {
                    throw new IllegalArgumentException("El QR procesado supera 2 MB; utiliza una imagen más ligera");
                }
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
                return new QrValidado(png, sha);
            } finally {
                reader.dispose();
            }
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("No se pudo validar la imagen QR", ex);
        } catch (IndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("Imagen QR dañada", ex);
        }
    }
}
