package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.EditorTiendaService;
import com.tiendasebas.tiendasebas.service.EditorTiendaService.Borrador;
import com.tiendasebas.tiendasebas.service.EditorTiendaService.Version;
import com.tiendasebas.tiendasebas.service.CabeceraTiendaService.CabeceraConfig;
import com.tiendasebas.tiendasebas.service.FooterTiendaService.FooterConfig;
import com.tiendasebas.tiendasebas.service.HomeImagenService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tiendas/admin/editor")
@PreAuthorize("hasRole('ADMIN')")
public class EditorTiendaController {
    private final EditorTiendaService editor;

    private final ObjectProvider<HomeImagenService> imagenes;

    public EditorTiendaController(EditorTiendaService editor, ObjectProvider<HomeImagenService> imagenes) {
        this.editor = editor;
        this.imagenes = imagenes;
    }

    @GetMapping("/borrador")
    public Borrador obtener() {
        return editor.obtener();
    }

    @PutMapping("/borrador/cabecera")
    public Borrador guardarCabecera(@RequestParam long revision, @RequestBody CabeceraConfig datos) {
        return editor.guardarCabecera(revision, datos);
    }

    @PutMapping("/borrador/footer")
    public Borrador guardarFooter(@RequestParam long revision, @RequestBody FooterConfig datos) {
        return editor.guardarFooter(revision, datos);
    }

    @PutMapping("/borrador/home")
    public Borrador guardarHome(@RequestParam long revision, @RequestBody List<String> secciones) {
        return editor.guardarHome(revision, secciones);
    }

    @GetMapping("/recursos-home")
    public EditorTiendaService.Portada recursosHome() {
        return editor.recursosHome();
    }

    @PutMapping("/borrador/portada")
    public Borrador guardarPortada(@RequestParam long revision, @RequestBody EditorTiendaService.Portada datos) {
        return editor.guardarPortada(revision, datos);
    }

    @PostMapping(value = "/borrador/portada/{destino}/{id}/imagen", consumes = "multipart/form-data")
    public Borrador subirImagen(@PathVariable EditorTiendaService.DestinoImagen destino, @PathVariable int id,
                               @RequestParam long revision, @RequestParam MultipartFile imagen) {
        return editor.subirImagen(revision, destino, id, imagen, imagenes.getObject());
    }

    @PutMapping("/borrador/tema")
    public Borrador guardarTema(@RequestParam long revision, @RequestBody EditorTiendaService.Tema tema) {
        return editor.guardarTema(revision, tema);
    }

    @PostMapping("/publicar")
    public Borrador publicar(@RequestParam long revision, Principal usuario) {
        return editor.publicar(revision, usuario.getName());
    }

    @GetMapping("/versiones")
    public List<Version> historial() {
        return editor.historial();
    }

    @PostMapping("/versiones/{version}/restaurar")
    public Borrador restaurar(@PathVariable long version, @RequestParam long revision) {
        return editor.restaurar(version, revision);
    }
}
