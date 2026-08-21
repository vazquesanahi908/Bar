package com.barclub.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "detalle_pedidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Column(nullable = false)
    private Integer cantidad;

    @NotNull(message = "El precio unitario es obligatorio")
    @Column(nullable = false)
    private Double precioUnitario;

    @Column(nullable = false)
    private Double subtotal;

    // Costo unitario CONGELADO al momento de la venta (igual que precioUnitario).
    // Si mañana sube el costo del producto, los reportes históricos no se distorsionan.
    @Column
    private Double costoUnitario;

    // Variante elegida (ej: "Entera", "Media", "Fritas"). Se guarda para que la
    // cocina sepa qué preparar y quede registrado en el historial.
    @Column(length = 60)
    private String variante;

    // Cantidad con la que se cargó este renglón la primera vez (al crear el
    // pedido, o al agregarlo en una edición posterior). NUNCA se toca
    // después, aunque cambiarCantidadDetalle actualice "cantidad". Sirve
    // para que Cocina pueda mostrar de forma durable (sin depender de nada
    // en memoria del navegador, ni de cuánto tiempo pase) qué productos son
    // nuevos (cantidadOriginal = 0) o cambiaron de cantidad después de
    // creado el pedido — reportado en QA: el resaltado se perdía al
    // refrescar la pantalla de Cocina.
    @Column
    @Builder.Default
    private Integer cantidadOriginal = 0;

    // Soft delete: en vez de borrar la fila cuando se saca un producto de un
    // pedido ya creado, se marca "eliminado" y se conserva. Así Cocina puede
    // seguir mostrando qué se sacó (tachado) aunque recargue la pantalla,
    // hasta que el pedido pase a LISTO. Se excluye del total y de la lista
    // de productos "activos" del pedido en todos los demás lugares.
    @Column
    @Builder.Default
    private Boolean eliminado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // Calcula el subtotal antes de persistir
    @PrePersist
    @PreUpdate
    public void calcularSubtotal() {
        if (cantidad != null && precioUnitario != null) {
            this.subtotal = cantidad * precioUnitario;
        }
    }
}
