package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.Genero;
import com.tiendasebas.tiendasebas.repository.GeneroRepository;

import org.springframework.web.bind.annotation.*;

        import java.util.List;

@RestController
@RequestMapping("/api/generos")
public class GeneroController {

    private final GeneroRepository generoRepository;

    public GeneroController(GeneroRepository generoRepository) {
        this.generoRepository = generoRepository;
    }

    @GetMapping
    public List<Genero> listar() {
        return generoRepository.findAll();
    }
}
