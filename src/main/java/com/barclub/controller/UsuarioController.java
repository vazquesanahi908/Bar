package com.barclub.controller;

import com.barclub.config.JwtUtil;
import com.barclub.dto.LoginRequestDTO;
import com.barclub.dto.UsuarioRequestDTO;
import com.barclub.dto.UsuarioResponseDTO;
import com.barclub.exception.ErrorResponse;
import com.barclub.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema y autenticación")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Listar todos los usuarios")
    @ApiResponse(responseCode = "200", description = "Lista de usuarios")
    public ResponseEntity<List<UsuarioResponseDTO>> listarTodos() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @PostMapping("/login")
    @Operation(summary = "Login de usuario", description = "Valida email y contraseña. Devuelve los datos del usuario y el token JWT.")
    @ApiResponse(responseCode = "200", description = "Login exitoso")
    @ApiResponse(responseCode = "401", description = "Email o contraseña incorrectos",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto) {
        return usuarioService.login(dto.getEmail(), dto.getPassword())
                .<ResponseEntity<?>>map(usuario -> {
                    String token = jwtUtil.generarToken(usuario.getEmail(), usuario.getRol().name());
                    return ResponseEntity.ok(Map.of(
                            "token", token,
                            "usuario", usuario
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Email o contraseña incorrectos")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por ID")
    @ApiResponse(responseCode = "200", description = "Usuario encontrado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear usuario")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "400", description = "Email ya en uso o datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponseDTO> crear(@Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario")
    @ApiResponse(responseCode = "200", description = "Usuario actualizado")
    @ApiResponse(responseCode = "400", description = "Email ya en uso",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponseDTO> actualizar(
            @PathVariable Long id, @Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario")
    @ApiResponse(responseCode = "204", description = "Usuario eliminado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña con clave maestra",
               description = "Valida la clave maestra en el servidor y actualiza la contraseña del usuario.")
    @ApiResponse(responseCode = "200", description = "Contraseña actualizada")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        usuarioService.resetPasswordConClaveMaestra(
                body.get("email"), body.get("claveMaestra"), body.get("nuevaPassword"));
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada"));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Cambiar mi propia contraseña",
               description = "El usuario ya logueado cambia su contraseña probando que conoce la actual. " +
                       "No requiere la clave maestra: se usa sobre todo para salir de la contraseña de fábrica.")
    @ApiResponse(responseCode = "200", description = "Contraseña actualizada")
    @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o nueva contraseña inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Map<String, String>> cambiarMiPassword(
            Authentication authentication, @RequestBody Map<String, String> body) {
        String email = authentication != null ? String.valueOf(authentication.getPrincipal()) : null;
        usuarioService.cambiarPasswordPropia(email, body.get("passwordActual"), body.get("passwordNueva"));
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada"));
    }
}
