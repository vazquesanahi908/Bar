package com.barclub.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Una cuenta a la que el cliente puede transferir a mano (alias de Mercado
 * Pago, de Uala, CBU de un banco, lo que el local use). El dueño las carga
 * desde Configuración → Pagos y elige él mismo el texto de cada una — el
 * sistema no sabe ni necesita saber de qué app es cada alias, solo lo
 * muestra tal cual en la web pública cuando el cliente elige "Transferencia".
 */
@Entity
@Table(name = "cuentas_pago")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre libre para identificarla: "Mercado Pago", "Uala", "Banco Galicia"...
    @Column(nullable = false, length = 60)
    private String etiqueta;

    // Alias, CBU/CVU o cualquier dato que el cliente necesite para transferir.
    @Column(nullable = false, length = 200)
    private String alias;

    // Para poder desactivar una cuenta temporalmente sin borrarla (y perder
    // el historial de que existió). Solo se muestran al público las activas.
    @Column
    @Builder.Default
    private Boolean activa = true;
}
