package com.barclub.dto;

import com.barclub.entity.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponseDTO {
    private Long id;
    private String nombre;
    private String email;
    private Rol rol;

    // Se calcula solo en el login (ver UsuarioService.login): true si el usuario
    // sigue entrando con la contraseña de fábrica que carga DataInitializer al
    // primer arranque (admin123, cajero123, cocina123, mozo123). El panel usa
    // este flag para obligar a poner una contraseña propia antes de dejar
    // seguir usando el sistema. En cualquier otro endpoint queda en false.
    @Builder.Default
    private boolean debeCambiarPassword = false;
}
