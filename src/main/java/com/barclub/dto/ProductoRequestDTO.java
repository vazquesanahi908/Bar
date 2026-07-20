package com.barclub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductoRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private Double precio;

    @DecimalMin(value = "0.0", message = "El costo no puede ser negativo")
    private Double costo;

    private Boolean activo = true;

    private String imagenUrl;

    @NotBlank(message = "La categoría es obligatoria")
    private String categoria;
}
