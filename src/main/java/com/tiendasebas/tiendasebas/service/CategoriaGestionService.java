
package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.Categoria;
import com.tiendasebas.tiendasebas.repository.CategoriaRepository;
import com.tiendasebas.tiendasebas.repository.ProductoRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class CategoriaGestionService {

    private final CategoriaRepository repository;
    private final ProductoRepository productoRepository;

    public CategoriaGestionService(
            CategoriaRepository repository,
            ProductoRepository productoRepository
    ) {
        this.repository = repository;
        this.productoRepository = productoRepository;
    }

    private Long tiendaActual() {
        Long id = TenantContext.getCurrentTenant();

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tienda no identificada"
            );
        }

        return id;
    }

    @Transactional(readOnly = true)
    public List<Categoria> listar() {
        return repository.findByTiendaId(tiendaActual())
                .stream()
                .sorted(
                        Comparator.comparing(Categoria::getIdCategoria)
                )
                .toList();
    }

    private String validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nombre es obligatorio"
            );
        }

        String limpio = nombre.trim();

        if (limpio.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximo 100 caracteres"
            );
        }

        return limpio;
    }

    private void comprobarDuplicado(
            Long tiendaId,
            Integer excluirId,
            String nombre
    ) {
        boolean existe = repository.findByTiendaId(tiendaId)
                .stream()
                .anyMatch(c ->
                        !c.getIdCategoria().equals(excluirId) &&
                                c.getNombreCategoria().equalsIgnoreCase(nombre)
                );

        if (existe) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Esta categoria ya existe en la tienda"
            );
        }
    }

    @Transactional
    public Categoria crear(String nombre, Boolean estado) {
        Long tiendaId = tiendaActual();
        String limpio = validarNombre(nombre);

        comprobarDuplicado(tiendaId, null, limpio);

        Categoria nueva = new Categoria();
        nueva.setIdCategoria(null);
        nueva.setNombreCategoria(limpio);
        nueva.setEstado(estado == null || estado);
        nueva.setTiendaId(tiendaId);

        return repository.save(nueva);
    }

    @Transactional
    public Categoria actualizar(
            Integer id,
            String nombre,
            Boolean estado
    ) {
        Long tiendaId = tiendaActual();

        Categoria categoria = repository
                .findByIdCategoriaAndTiendaId(id, tiendaId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Categoria no encontrada"
                        )
                );

        String limpio = validarNombre(nombre);

        comprobarDuplicado(tiendaId, id, limpio);

        categoria.setNombreCategoria(limpio);

        if (estado != null) {
            categoria.setEstado(estado);
        }

        return repository.save(categoria);
    }

    @Transactional
    public void eliminar(Integer id) {
        Long tiendaId = tiendaActual();

        Categoria categoria = repository
                .findByIdCategoriaAndTiendaId(id, tiendaId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Categoria no encontrada"
                        )
                );

        boolean tieneProductos = productoRepository
                .existsByCategoriaIdCategoriaAndTiendaId(
                        id,
                        tiendaId
                );

        if (tieneProductos) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No puedes eliminar esta categoria porque " +
                            "tiene productos asociados. Puedes desactivarla."
            );
        }

        // La base de datos también protege otras
        // referencias mediante sus claves foráneas.
        try {
            repository.delete(categoria);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede eliminar la categoria porque " +
                            "tiene registros relacionados.",
                    e
            );
        }
    }
}
