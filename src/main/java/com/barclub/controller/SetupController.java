package com.barclub.controller;

import com.barclub.service.SetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
@Tag(name = "Puesta a punto", description = "Utilidades para preparar una instalación nueva antes de entregarla a un cliente")
public class SetupController {

    private final SetupService setupService;

    @PostMapping("/reset-demo-data")
    @Operation(summary = "Borrar menú y clientes de ejemplo",
               description = "Borra todos los productos y clientes cargados (el menú de ejemplo que trae la " +
                       "instalación nueva). Se niega si ya hay pedidos cargados, para no romper datos reales.")
    @ApiResponse(responseCode = "200", description = "Datos de ejemplo borrados")
    public ResponseEntity<Map<String, Object>> resetDemoData(
            @RequestParam(name = "confirmar", defaultValue = "false") boolean confirmar) {
        return ResponseEntity.ok(setupService.borrarDatosDeEjemplo(confirmar));
    }
}
