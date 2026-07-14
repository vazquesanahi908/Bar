package com.barclub.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ReservaRequestDTO {

    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String nombreCliente;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "La hora es obligatoria")
    private LocalTime hora;

    @Min(value = 1, message = "Debe haber al menos 1 persona")
    @Max(value = 20, message = "Máximo 20 personas")
    private Integer cantidadPersonas;

    private String telefono;

    private String aclaraciones;

    // Opcional: cliente registrado
    private Long clienteId;
}
