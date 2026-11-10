package com.tiendasebas.tiendasebas.yape;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class YapeQrValidatorTest {
    private byte[] imagen(String formato) throws Exception {
        BufferedImage original = new BufferedImage(220, 220, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 220; x++) for (int y = 0; y < 220; y++) {
            original.setRGB(x, y, (x + y) % 2 == 0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(original, formato, salida));
        return salida.toByteArray();
    }

    @Test void pngAceptado() throws Exception {
        var qr = YapeQrValidator.validar(imagen("png"));
        assertTrue(qr.imagen().length > 0);
        assertEquals(64, qr.sha256().length());
        assertEquals(220, ImageIO.read(new java.io.ByteArrayInputStream(qr.imagen())).getWidth());
    }

    @Test void jpegSeNormalizaAPng() throws Exception {
        var qr = YapeQrValidator.validar(imagen("jpeg"));
        assertEquals((byte) 0x89, qr.imagen()[0]);
        assertEquals((byte) 0x50, qr.imagen()[1]);
    }

    @Test void rechazaContenidoNoImagen() {
        assertThrows(IllegalArgumentException.class,
                () -> YapeQrValidator.validar("no-es-un-qr".getBytes()));
    }

    @Test void rechazaArchivoExcesivo() {
        assertThrows(IllegalArgumentException.class,
                () -> YapeQrValidator.validar(new byte[YapeQrValidator.MAX_BYTES + 1]));
    }

    @Test void rechazaImagenDemasiadoPequena() throws Exception {
        BufferedImage pequeña = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        var salida = new ByteArrayOutputStream();
        ImageIO.write(pequeña, "png", salida);
        assertThrows(IllegalArgumentException.class,
                () -> YapeQrValidator.validar(salida.toByteArray()));
    }
}
