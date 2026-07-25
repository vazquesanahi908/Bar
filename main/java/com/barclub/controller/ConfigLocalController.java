package com.barclub.controller;

import com.barclub.entity.ConfigLocal;
import com.barclub.service.ConfigLocalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        ConfigLocal cfg = service.obtener();
        // La página pública necesita esta configuración, pero la lista de emails
        // del personal no debe salir al público: sería una lista de usuarios
        // válidos para intentar adivinar contraseñas. Solo va a quien está logueado.
        if (!estaAutenticado()) {
            cfg = cfg.toBuilder().loginEmails("[]").build();
        }
        return ResponseEntity.ok(cfg);
    }

    private boolean estaAutenticado() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated()
                && !"anonymousUser".equals(String.valueOf(a.getPrincipal()));
    }

    @PutMapping
    @Operation(summary = "Guardar configuración del local", description = "Actualiza la configuración. Solo debe ser usado por el admin.")
    @ApiResponse(responseCode = "200", description = "Configuración actualizada")
    public ResponseEntity<ConfigLocal> guardar(@RequestBody ConfigLocal cfg) {
        return ResponseEntity.ok(service.guardar(cfg));
    }
}
