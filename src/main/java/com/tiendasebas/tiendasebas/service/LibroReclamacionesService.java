package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Primera pieza interna del Libro de Reclamaciones. No expone rutas HTTP.
 * La migracion V20 debe aplicarse y sus privilegios verificarse por separado.
 */
@Service
public class LibroReclamacionesService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public LibroReclamacionesService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
    }

    public record Solicitud(
            String tipo,
            String consumidorNombre,
            String documentoTipo,
            String documentoNumero,
            String domicilio,
            String correo,
            String telefono,
            boolean menorEdad,
            String representanteNombre,
            String bienTipo,
            String bienDescripcion,
            BigDecimal montoReclamado,
            String detalle,
            String pedido
    ) {}

    public record Registro(long numeroHoja, long id) {}

    /**
     * Invocar solo despues de que los filtros hayan resuelto la tienda por host.
     * Nunca recibe tiendaId desde el consumidor.
     */
    public Registro registrar(Solicitud datos) {
        if (datos == null) throw badRequest("Solicitud requerida");
        validar(datos);

        final long tiendaId = TenantContext.getCurrentTenant();
        Registro registro = tx.execute(status -> {
            // SET LOCAL: solo vive durante esta transaccion y conexion.
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, Long.toString(tiendaId));

            Long numero = jdbc.queryForObject("""
                    INSERT INTO public.saas_reclamaciones_numeracion (tienda_id, ultimo_numero)
                    VALUES (?, 1)
                    ON CONFLICT (tienda_id)
                    DO UPDATE SET ultimo_numero = public.saas_reclamaciones_numeracion.ultimo_numero + 1
                    RETURNING ultimo_numero
                    """, Long.class, tiendaId);
            if (numero == null) throw new IllegalStateException("No se genero numero de hoja");

            Long id = jdbc.queryForObject("""
                    INSERT INTO public.saas_reclamaciones
                    (tienda_id, numero_hoja, tipo, consumidor_nombre,
                     documento_tipo, documento_numero, domicilio, correo, telefono,
                     menor_edad, representante_nombre, bien_tipo, bien_descripcion,
                     monto_reclamado, detalle, pedido)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class,
                    tiendaId, numero, normalizarEnum(datos.tipo()), limpiar(datos.consumidorNombre()),
                    limpiar(datos.documentoTipo()), limpiar(datos.documentoNumero()),
                    limpiar(datos.domicilio()), opcional(datos.correo()), opcional(datos.telefono()),
                    datos.menorEdad(), opcional(datos.representanteNombre()),
                    normalizarEnum(datos.bienTipo()), limpiar(datos.bienDescripcion()),
                    datos.montoReclamado(), limpiar(datos.detalle()), limpiar(datos.pedido()));
            if (id == null) throw new IllegalStateException("No se genero identificador");

            jdbc.update("""
                    INSERT INTO public.saas_reclamaciones_eventos
                      (tienda_id, reclamacion_id, tipo, contenido, actor, estado)
                    VALUES (?, ?, 'RECEPCION', 'Reclamacion recibida', 'SISTEMA', 'RECIBIDO')
                    """, tiendaId, id);
            return new Registro(numero, id);
        });
        if (registro == null) throw new IllegalStateException("No se completo la transaccion");
        return registro;
    }

    private static void validar(Solicitud d) {
        exigirEnum(d.tipo(), "RECLAMO", "QUEJA");
        exigirTexto(d.consumidorNombre(), 200, "Nombre");
        exigirTexto(d.documentoTipo(), 20, "Tipo de documento");
        exigirTexto(d.documentoNumero(), 30, "Numero de documento");
        exigirTexto(d.domicilio(), 2000, "Domicilio");
        exigirTexto(d.bienDescripcion(), 4000, "Descripcion del bien");
        exigirEnum(d.bienTipo(), "PRODUCTO", "SERVICIO");
        exigirTexto(d.detalle(), 12000, "Detalle");
        exigirTexto(d.pedido(), 12000, "Pedido");
        if (d.correo() != null && d.correo().length() > 254) throw badRequest("Correo demasiado largo");
        if (d.telefono() != null && d.telefono().length() > 30) throw badRequest("Telefono demasiado largo");
        if (d.representanteNombre() != null && d.representanteNombre().length() > 200)
            throw badRequest("Representante demasiado largo");
        if (d.menorEdad()) exigirTexto(d.representanteNombre(), 200, "Representante");
        if (opcional(d.correo()) == null && opcional(d.telefono()) == null)
            throw badRequest("Se requiere un medio de contacto");
        if (d.montoReclamado() != null && (d.montoReclamado().signum() < 0
                || d.montoReclamado().precision() > 12 || d.montoReclamado().scale() > 2))
            throw badRequest("Monto invalido");
    }

    private static void exigirTexto(String valor, int max, String campo) {
        if (valor == null || valor.isBlank() || valor.length() > max)
            throw badRequest(campo + " es obligatorio o excede la longitud permitida");
    }

    private static void exigirEnum(String valor, String a, String b) {
        String normal = normalizarEnum(valor);
        if (!a.equals(normal) && !b.equals(normal)) throw badRequest("Valor de catalogo invalido");
    }

    private static String normalizarEnum(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private static String limpiar(String valor) { return valor.trim(); }
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
