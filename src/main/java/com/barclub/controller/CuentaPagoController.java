package com.barclub.controller;

import com.barclub.entity.CuentaPago;
import com.barclub.service.CuentaPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cuentas para transferencia manual (alias de Mercado Pago, Uala, CBU de
 * banco, etc.) que el dueño carga desde Configuración → Pagos, y que la web
 * pública muestra cuando el cliente elige pagar por "Transferencia".
 */
@RestController
@RequestMapping("/api/cuentas-pago")
@RequiredArgsConstructor
@Tag(name = "Cuentas de pago", description = "Alias/CBU para transferencia manual (Mercado Pago, Uala, banco, etc.)")
public class CuentaPagoController {

    private final CuentaPagoService service;

    @GetMapping
    @Operation(summary = "Listar cuentas de pago",
               description = "Con soloActivas=true (lo que usa la web pública) devuelve solo las que el dueño dejó activas.")
    public ResponseEntity<List<CuentaPago>> listar(@RequestParam(required = false) Boolean soloActivas) {
        return ResponseEntity.ok(Boolean.TRUE.equals(soloActivas) ? service.listarActivas() : service.listarTodas());
    }

    @PostMapping
    @Operation(summary = "Agregar una cuenta de pago")
    public ResponseEntity<CuentaPago> crear(@RequestBody CuentaPago cuenta) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(cuenta));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una cuenta de pago")
    public ResponseEntity<CuentaPago> actualizar(@PathVariable Long id, @RequestBody CuentaPago cuenta) {
        return ResponseEntity.ok(service.actualizar(id, cuenta));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una cuenta de pago")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
