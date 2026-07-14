package com.barclub.dto;

import com.barclub.entity.TipoPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalTime;
import java.util.List;

@Data
public class PedidoRequestDTO {

    @NotNull(message = "El tipo de pedido es obligatorio")
    private TipoPedido tipo;

    // Opcional: cliente registrado
    private Long clienteId;

    // Obligatorio: id del usuario (cajero/empleado que lo registra)
    @NotNull(message = "El usuario es obligatorio")
    private Long usuarioId;

    // Para pedidos sin cuenta o delivery
    private String nombreCliente;
    private String telefonoCliente;
    private String direccionEntrega;
    private LocalTime horarioEntrega;

    // Nº de mesa (pedidos en salón)
    private String mesa;

    @NotEmpty(message = "El pedido debe tener al menos un producto")
    @Valid
    private List<DetallePedidoRequestDTO> detalles;
}
