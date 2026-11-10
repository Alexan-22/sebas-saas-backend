package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.TiendaConfigRequest;
import com.tiendasebas.tiendasebas.entity.Tienda;
import com.tiendasebas.tiendasebas.repository.TiendaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;

@Service
public class TiendaIdentidadService {
    private final TiendaRepository tiendas;
    private final SupabaseStorageService storage;

    @Value("${app.storage.provider:local}") private String proveedor;
    @Value("${app.upload.dir:uploads}") private String uploadDir;

    public TiendaIdentidadService(TiendaRepository tiendas, SupabaseStorageService storage) {
        this.tiendas = tiendas;
        this.storage = storage;
    }

    private Tienda actual() {
        return tiendas.findById(TenantContext.getCurrentTenant())
                .filter(t -> "ACTIVA".equals(t.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no disponible"));
    }

    private String texto(String valor, int max, boolean obligatorio) {
        String limpio = valor == null ? "" : valor.trim();
        if ((obligatorio && limpio.isEmpty()) || limpio.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo de configuracion invalido");
        }
        return limpio.isEmpty() ? null : limpio;
    }

    private String color(String valor) {
        if (valor == null || !valor.matches("#[0-9a-fA-F]{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Color hexadecimal invalido");
        }
        return valor.toUpperCase(Locale.ROOT);
    }

    private String telefono(String valor) {
        String t = texto(valor, 20, false);
        if (t != null && !t.matches("[+0-9 ()-]{6,20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefono invalido");
        }
        return t;
    }

    @Transactional
    public Tienda actualizar(TiendaConfigRequest r) {
        if (r == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan datos");
        String nombre = texto(r.nombre(), 100, true);
        String colorPrimario = color(r.colorPrimario());
        String colorSecundario = color(r.colorSecundario());
        String contacto = telefono(r.telefonoSoporte());
        String whatsapp = telefono(r.whatsapp());
        String descripcion = texto(r.descripcionComercial(), 500, false);
        Tienda t = actual();
        t.setNombre(nombre);
        t.setColorPrimario(colorPrimario);
        t.setColorSecundario(colorSecundario);
        t.setTelefonoSoporte(contacto);
        t.setWhatsapp(whatsapp);
        t.setDescripcionComercial(descripcion);
        return tiendas.save(t);
    }

    @Transactional
    public Tienda subirLogo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > 2L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo vacio o mayor de 2 MB");
        }
        String extension = detectarTipo(archivo);
        Tienda t = actual();
        String nombre = UUID.randomUUID() + "." + extension;
        String url;
        if ("supabase".equalsIgnoreCase(proveedor)) {
            String ruta = "tiendas/" + t.getId() + "/logos/" + nombre;
            storage.subir(archivo, ruta);
            url = storage.obtenerUrl(ruta);
        } else if ("local".equalsIgnoreCase(proveedor)) {
            try {
                Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
                Files.createDirectories(dir);
                Path destino = dir.resolve("tienda-" + t.getId() + "-logo-" + nombre).normalize();
                if (!destino.startsWith(dir)) throw new IOException("Ruta fuera del directorio");
                try (var input = archivo.getInputStream()) {
                    Files.copy(input, destino);
                }
                url = "/uploads/" + destino.getFileName();
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el logo", ex);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Proveedor de almacenamiento invalido");
        }
        t.setLogoUrl(url);
        return tiendas.save(t);
    }

    private String detectarTipo(MultipartFile f) {
        byte[] b;
        try {
            b = f.getInputStream().readNBytes(16);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo ilegible", ex);
        }
        String declared = f.getContentType();
        if (b.length >= 3 && (b[0] & 255) == 0xFF && (b[1] & 255) == 0xD8 && (b[2] & 255) == 0xFF
                && "image/jpeg".equalsIgnoreCase(declared)) return "jpg";
        if (b.length >= 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71
                && "image/png".equalsIgnoreCase(declared)) return "png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P'
                && "image/webp".equalsIgnoreCase(declared)) return "webp";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo JPG, PNG o WebP validos");
    }
}
