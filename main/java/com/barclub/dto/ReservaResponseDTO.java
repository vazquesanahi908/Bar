package com.barclub.dto;

import com.barclub.entity.Reserva;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaResponseDTO {
    private Long id;
    private String nombreCliente;
    private LocalDate fecha;
    private LocalTime hora;
    private Integer cantidadPersonas;
    private String telefono;
    private String aclaraciones;
    private Reserva.EstadoReserva estado;
    private ClienteResponseDTO cliente;
    private boolean cancelable;
}
