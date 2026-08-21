package com.barclub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetallePedidoResponseDTO {
    private Long id;
    private Integer cantidad;
    private Double precioUnitario;
    private Double subtotal;
    private String variante;
    private ProductoResponseDTO producto;
    // Cantidad con la que se cargó este renglón originalmente (0 si se agregó
    // en una edición posterior a la creación del pedido). Cocina lo usa para
    // marcar productos nuevos o con la cantidad cambiada, de forma durable.
    private Integer cantidadOriginal;
}
