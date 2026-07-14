package com.barclub.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "config_local")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigLocal {

    @Id
    private Long id; // siempre será 1, fila única

    private String nombre;

    @Column(length = 500)
    private String dir;

    private String tel;

    @Column(length = 500)
    private String slogan;

    private String dias;
    @JsonProperty("mDesde")
    private String mDesde;
    @JsonProperty("mHasta")
    private String mHasta;
    @JsonProperty("nDesde")
    private String nDesde;
    @JsonProperty("nHasta")
    private String nHasta;

    @Column(length = 500)
    private String horarioLibre;

    private String ig;
    private String wa;
    private String fb;

    @Builder.Default
    private Integer radioDelivery = 5;

    @Builder.Default
    private Integer costoDelivery = 0;

    @Builder.Default
    private Integer minimo = 0;

    @Builder.Default
    private Boolean aceptaDelivery = true;

    @Builder.Default
    private Boolean aceptaRetiro = true;

    @Builder.Default
    private Boolean avisoOn = false;

    @Column(length = 500)
    private String avisoTxt;

    // "auto", "open", "close"  — controlado desde el panel admin
    @Builder.Default
    private String estadoManual = "auto";

    // Logo del local — puede ser URL externa o base64 de imagen
    @Column(columnDefinition = "MEDIUMTEXT")
    private String logoUrl;

    // Imagen de portada/banner del hero — puede ser URL externa o base64 de imagen
    @Column(columnDefinition = "MEDIUMTEXT")
    private String heroUrl;

    // Posición del banner: "center", "top", "bottom", etc.
    @Builder.Default
    private String heroPos = "center";

    // Momento del último cierre de caja (ISO datetime). Vacío = nunca se cerró.
    private String cierreCaja;

    // Emails habilitados para acceso rápido en el login del panel admin
    // Guardados como JSON array: ["email1@x.com","email2@x.com"]
    @Column(length = 1000)
    @Builder.Default
    private String loginEmails = "[]";
}
