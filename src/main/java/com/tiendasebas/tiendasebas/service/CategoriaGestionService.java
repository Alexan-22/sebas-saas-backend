package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.entity.Categoria;
import com.tiendasebas.tiendasebas.repository.CategoriaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class CategoriaGestionService {
    private final CategoriaRepository repository;

    public CategoriaGestionService(CategoriaRepository repository) {
        this.repository = repository;
    }

    private Long tiendaActual() {
        Long id = TenantContext.getCurrentTenant();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no identificada");
        }
        return id;
    }

    @Transactional(readOnly = true)
    public List<Categoria> listar() {
        return repository.findByTiendaId(tiendaActual()).stream()
                .sorted(Comparator.comparing(Categoria::getIdCategoria))
                .toList();
    }

    private String validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximo 100 caracteres");
        }
        return limpio;
    }

    private void comprobarDuplicado(Long tiendaId, Integer excludeId, String nombre) {
        boolean existe = repository.findByTiendaId(tiendaId).stream()
                .anyMatch(c -> !c.getIdCategoria().equals(excludeId)
                        && c.getNombreCategoria().equalsIgnoreCase(nombre));
        if (existe) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta categoria ya existe en la tienda");
        }
    }

    @Transactional
    public Categoria crear(String nombre, Boolean estado) {
        Long tiendaId = tiendaActual();
        String nombreLimpio = validarNombre(nombre);
        comprobarDuplicado(tiendaId, null, nombreLimpio);
        Categoria nueva = new Categoria();
        nueva.setIdCategoria(null);
        nueva.setNombreCategoria(nombreLimpio);
        nueva.setEstado(estado == null || estado);
        // No confiar nunca en un tienda_id enviado desde Angular.
        nueva.setTiendaId(tiendaId);
        return repository.save(nueva);
    }

    @Transactional
    public Categoria actualizar(Integer id, String nombre, Boolean estado) {
        Long tiendaId = tiendaActual();
        Categoria c = repository.findByIdCategoriaAndTiendaId(id, tiendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada"));
        String nombreLimpio = validarNombre(nombre);
        comprobarDuplicado(tiendaId, id, nombreLimpio);
        c.setNombreCategoria(nombreLimpio);
        if (estado != null) {
            c.setEstado(estado);
        }
        // Nunca cambiar la tienda del registro al actualizar.
        return repository.save(c);
    }
}
