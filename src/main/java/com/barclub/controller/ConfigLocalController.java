package com.barclub.controller;

import com.barclub.entity.ConfigLocal;
import com.barclub.service.ConfigLocalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "Configuración", description = "Configuración general del local (horarios, delivery, redes sociales)")
public class ConfigLocalController {

    private final ConfigLocalService service;

    @GetMapping
    @Operation(summary = "Obtener configuración del local", description = "Devuelve nombre, horarios, dirección, redes sociales y opciones de delivery.")
    @ApiResponse(responseCode = "200", description = "Configuración actual del local")
    public ResponseEntity<ConfigLocal> obtener() {
        return ResponseEntity.ok(service.obtener());
    }

    @PutMapping
    @Operation(summary = "Guardar configuración del local", description = "Actualiza la configuración. Solo debe ser usado por el admin.")
    @ApiResponse(responseCode = "200", description = "Configuración actualizada")
    public ResponseEntity<ConfigLocal> guardar(@RequestBody ConfigLocal cfg) {
        return ResponseEntity.ok(service.guardar(cfg));
    }
}
