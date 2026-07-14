package com.barclub.controller;

import com.barclub.dto.DetallePedidoRequestDTO;
import com.barclub.dto.PedidoRequestDTO;
import com.barclub.dto.PedidoResponseDTO;
import com.barclub.entity.EstadoPedido;
import com.barclub.exception.ErrorResponse;
import com.barclub.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Pedidos", description = "Gestión de pedidos del bar (local, delivery y retiro)")
public class PedidoController {

    private final PedidoService pedidoService;

    @GetMapping
    @Operation(summary = "Listar todos los pedidos", description = "Devuelve todos los pedidos registrados en el sistema.")
    @ApiResponse(responseCode = "200", description = "Lista obtenida correctamente")
    public ResponseEntity<List<PedidoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(pedidoService.listarTodos());
    }

    @GetMapping("/activos")
    @Operation(summary = "Listar pedidos activos", description = "Devuelve pedidos en estado PENDIENTE, PREPARACION o LISTO.")
    @ApiResponse(responseCode = "200", description = "Lista de pedidos activos")
    public ResponseEntity<List<PedidoResponseDTO>> listarActivos() {
        return ResponseEntity.ok(pedidoService.listarActivos());
    }

    @GetMapping("/estado/{estado}")
    @Operation(summary = "Listar pedidos por estado", description = "Filtra pedidos según su estado: PENDIENTE, PREPARACION, LISTO, ENTREGADO o CANCELADO.")
    @ApiResponse(responseCode = "200", description = "Lista filtrada por estado")
    @ApiResponse(responseCode = "400", description = "Estado inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<PedidoResponseDTO>> listarPorEstado(@PathVariable EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.listarPorEstado(estado));
    }

    @GetMapping("/fecha")
    @Operation(summary = "Listar pedidos por fecha", description = "Devuelve pedidos de una fecha específica en formato YYYY-MM-DD.")
    @ApiResponse(responseCode = "200", description = "Lista filtrada por fecha")
    public ResponseEntity<List<PedidoResponseDTO>> listarPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(pedidoService.listarPorFecha(fecha));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener pedido por ID")
    @ApiResponse(responseCode = "200", description = "Pedido encontrado")
    @ApiResponse(responseCode = "404", description = "Pedido no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PedidoResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear nuevo pedido", description = "Crea un pedido con sus detalles. Requiere al menos un producto.")
    @ApiResponse(responseCode = "201", description = "Pedido creado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o falta dirección en delivery",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuario o producto no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PedidoResponseDTO> crear(@Valid @RequestBody PedidoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.crear(dto));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar estado del pedido", description = "Transiciones válidas: PENDIENTE→PREPARACION→LISTO→ENTREGADO. También PENDIENTE→CANCELADO.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Transición de estado inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Pedido no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PedidoResponseDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestParam EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.cambiarEstado(id, estado));
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar pedido", description = "Solo pedidos PENDIENTES pueden cancelarse, dentro de los 30 minutos de creación.")
    @ApiResponse(responseCode = "200", description = "Pedido cancelado")
    @ApiResponse(responseCode = "400", description = "No se puede cancelar (estado incorrecto o tiempo vencido)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Pedido no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PedidoResponseDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.cancelar(id));
    }

    @DeleteMapping("/entregados/hoy")
    @Operation(summary = "Eliminar pedidos entregados de hoy", description = "Borra todos los pedidos con estado ENTREGADO del día actual junto con sus ventas.")
    @ApiResponse(responseCode = "204", description = "Pedidos eliminados")
    public ResponseEntity<Void> eliminarEntregadosHoy() {
        pedidoService.eliminarEntregadosHoy();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/detalles")
    @Operation(summary = "Agregar producto a pedido existente", description = "Solo funciona en pedidos en estado PENDIENTE.")
    @ApiResponse(responseCode = "200", description = "Detalle agregado")
    @ApiResponse(responseCode = "400", description = "Pedido no está en estado PENDIENTE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Pedido o producto no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PedidoResponseDTO> agregarDetalle(
            @PathVariable Long id,
            @Valid @RequestBody DetallePedidoRequestDTO detalleDTO) {
        return ResponseEntity.ok(pedidoService.agregarDetalle(id, detalleDTO));
    }

    @DeleteMapping("/{pedidoId}/detalles/{detalleId}")
    @Operation(summary = "Eliminar producto de un pedido", description = "Solo funciona en pedidos en estado PENDIENTE.")
    @ApiResponse(responseCode = "200", description = "Detalle eliminado")
    @ApiResponse(responseCode = "400", description = "Pedido no está en estado PENDIENTE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Pedido no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PedidoResponseDTO> eliminarDetalle(
            @PathVariable Long pedidoId,
            @PathVariable Long detalleId) {
        return ResponseEntity.ok(pedidoService.eliminarDetalle(pedidoId, detalleId));
    }
}
