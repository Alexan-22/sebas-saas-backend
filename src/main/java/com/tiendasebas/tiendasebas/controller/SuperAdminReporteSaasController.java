package com.tiendasebas.tiendasebas.controller;

import com.tiendasebas.tiendasebas.service.ReporteComercialService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/reportes/saas")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminReporteSaasController {
    private final ReporteComercialService service;
    public SuperAdminReporteSaasController(ReporteComercialService service) { this.service = service; }
    @GetMapping("/resumen")
    public Map<String,Object> resumen(@RequestParam Map<String,String> filtros) { return service.resumen(filtros); }
    @GetMapping("/planes")
    public List<Map<String,Object>> porPlan(@RequestParam Map<String,String> filtros) { return service.porPlan(filtros); }
    @GetMapping("/suscripciones")
    public List<Map<String,Object>> suscripciones(@RequestParam Map<String,String> filtros) { return service.suscripciones(filtros); }
}
