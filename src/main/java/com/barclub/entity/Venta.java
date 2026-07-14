package com.barclub.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "ventas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Column(nullable = false)
    private Double total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPago metodoPago;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pedido pedido;
}
