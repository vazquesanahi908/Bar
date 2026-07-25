package com.barclub.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    @Column(nullable = false)
    private Double precio;

    // Costo de mercadería. Sin esto no hay margen y los rankings mienten.
    @Column
    private Double costo;

    // Precio de la variante grande (ej: pizza entera). Si está vacío, el producto
    // no tiene variantes y se cobra siempre el precio normal.
    @Column
    private Double precioEntera;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(length = 500)
    private String imagenUrl;

    @NotBlank(message = "La categoría es obligatoria")
    @Column(nullable = false)
    private String categoria;
}
