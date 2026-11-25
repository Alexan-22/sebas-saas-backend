package com.tiendasebas.tiendasebas.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renderiza una copia imprimible de una hoja persistida.
 * Servicio INTERNO: NO expone ruta HTTP, no concede acceso al consumidor.
 * La identidad legal del proveedor debe provenir de configuración verificada
 * del tenant, nunca de campos enviados por el consumidor.
 */
@Service
public class ConstanciaReclamacionesDocumentoService {
    public record Proveedor(String razonSocial, String ruc, String domicilio) {}

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm 'PET'");

    public String generarHtml(ConstanciaReclamacionesService.Hoja hoja, Proveedor proveedor) {
        if (hoja == null || hoja.recibidoEn() == null || hoja.numeroHoja() <= 0) {
            throw new IllegalArgumentException("Hoja persistida invalida");
        }
        if (proveedor == null || vacio(proveedor.razonSocial()) || vacio(proveedor.ruc())
                || vacio(proveedor.domicilio())) {
            throw new IllegalArgumentException("Identidad legal del proveedor incompleta");
        }
        if (!proveedor.ruc().trim().matches("\\d{11}")) {
            throw new IllegalArgumentException("RUC del proveedor invalido");
        }
        String fecha = hoja.recibidoEn().atZoneSameInstant(ZoneId.of("America/Lima")).format(FECHA);
        StringBuilder html = new StringBuilder(4500);
        html.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>Constancia del Libro de Reclamaciones</title>");
        html.append("<style>body{font-family:Arial,sans-serif;max-width:820px;margin:24px auto;padding:0 18px;color:#222}h1{font-size:23px}h2{font-size:16px;margin-top:25px;border-bottom:1px solid #aaa;padding-bottom:5px}dt{font-weight:bold;margin-top:10px}dd{margin:3px 0 8px;white-space:pre-wrap;overflow-wrap:anywhere}p{line-height:1.45}.meta{background:#f2f2f2;padding:12px}@media print{body{margin:0;padding:0}.meta{background:#eee}}</style>");
        html.append("</head><body><h1>Libro de Reclamaciones - Constancia de recepción</h1>");
        html.append("<p class=\"meta\"><strong>Hoja N.º ").append(hoja.numeroHoja())
                .append("</strong> · Recibida el ").append(e(fecha)).append("</p>");
        html.append("<h2>Identificación del proveedor</h2><dl>");
        campo(html, "Razón social", proveedor.razonSocial());
        campo(html, "RUC", proveedor.ruc());
        campo(html, "Domicilio", proveedor.domicilio());
        html.append("</dl><h2>Identificación del consumidor</h2><dl>");
        campo(html, "Nombre", hoja.consumidorNombre());
        campo(html, "Documento", combinar(hoja.documentoTipo(), hoja.documentoNumero()));
        campo(html, "Domicilio", hoja.domicilio());
        campo(html, "Correo", hoja.correo());
        campo(html, "Teléfono", hoja.telefono());
        campo(html, "Menor de edad", hoja.menorEdad() ? "Sí" : "No");
        if (hoja.menorEdad()) campo(html, "Representante", hoja.representanteNombre());
        html.append("</dl><h2>Bien contratado</h2><dl>");
        campo(html, "Tipo", hoja.bienTipo());
        campo(html, "Descripción", hoja.bienDescripcion());
        BigDecimal monto = hoja.montoReclamado();
        campo(html, "Monto reclamado", monto == null ? "No indicado" : "S/ " + monto.toPlainString());
        html.append("</dl><h2>Reclamación</h2><dl>");
        campo(html, "Tipo", hoja.tipo());
        campo(html, "Detalle", hoja.detalle());
        campo(html, "Pedido del consumidor", hoja.pedido());
        html.append("</dl><p>Esta constancia acredita la recepción de la reclamación identificada arriba; no acredita su resolución.</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private static void campo(StringBuilder html, String titulo, String valor) {
        html.append("<dt>").append(e(titulo)).append("</dt><dd>")
                .append(e(vacio(valor) ? "No indicado" : valor.trim())).append("</dd>");
    }

    private static String combinar(String a, String b) {
        return (a == null ? "" : a.trim()) + " " + (b == null ? "" : b.trim());
    }
    private static boolean vacio(String value) { return value == null || value.isBlank(); }
    private static String e(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
