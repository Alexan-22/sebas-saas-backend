package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.entity.Talla;
import com.tiendasebas.tiendasebas.repository.TallaRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tallas")
public class TallaController {

    private final TallaRepository tallaRepository;

    public TallaController(TallaRepository tallaRepository) {
        this.tallaRepository = tallaRepository;
    }

    @GetMapping
    public List<Talla> listar() {
        return tallaRepository.findAll();
    }
}