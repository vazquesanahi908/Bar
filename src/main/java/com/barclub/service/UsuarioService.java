package com.barclub.service;

import com.barclub.dto.UsuarioRequestDTO;
import com.barclub.dto.UsuarioResponseDTO;
import com.barclub.entity.Usuario;
import com.barclub.entity.Rol;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.master-key:admin2026}")
    private String masterKey;

    /**
     * Restablece la contraseña de un usuario validando la clave maestra.
     * La clave se verifica ACÁ, en el servidor: nunca viaja al HTML del panel.
     */
    // Protección contra fuerza bruta: máx. 5 intentos fallidos cada 10 minutos,
    // pero contados POR CLAVE (por email, normalmente) y no en un solo contador
    // global. Antes era un único contador compartido por todo el sistema: bastaba
    // con que cualquiera fallara 5 veces (a propósito o no) para bloquear el login
    // de TODO el personal durante 10 minutos, lo cual es en sí mismo un agujero
    // (un ataque de denegación de servicio barato). Con un contador por clave,
    // fallar el login de un usuario no afecta a los demás.
    private static final int MAX_INTENTOS = 5;
    private static final long VENTANA_MS = 10 * 60 * 1000L;
    private static final int LIMITE_CLAVES_EN_MEMORIA = 5000;

    private static class IntentosInfo {
        int fallidos = 0;
        long ventanaInicio = 0;
    }

    private final Map<String, IntentosInfo> intentosPorClave = new ConcurrentHashMap<>();

    private void controlarIntentos(String clave) {
        limpiarMapaSiCrecioDemasiado();
        IntentosInfo info = intentosPorClave.computeIfAbsent(normalizarClave(clave), k -> new IntentosInfo());
        synchronized (info) {
            long ahora = System.currentTimeMillis();
            if (ahora - info.ventanaInicio > VENTANA_MS) { info.ventanaInicio = ahora; info.fallidos = 0; }
            if (info.fallidos >= MAX_INTENTOS) {
                throw new BusinessException("Demasiados intentos fallidos. Esperá unos minutos y volvé a intentar.");
            }
        }
    }

    private void registrarFallo(String clave) {
        IntentosInfo info = intentosPorClave.computeIfAbsent(normalizarClave(clave), k -> new IntentosInfo());
        synchronized (info) {
            if (info.fallidos == 0) info.ventanaInicio = System.currentTimeMillis();
            info.fallidos++;
        }
    }

    private void limpiarIntentos(String clave) {
        IntentosInfo info = intentosPorClave.get(normalizarClave(clave));
        if (info != null) { synchronized (info) { info.fallidos = 0; } }
    }

    private String normalizarClave(String clave) {
        return clave == null ? "?" : clave.trim().toLowerCase();
    }

    // Salvaguarda simple: si alguien intenta agotar memoria mandando miles de
    // emails distintos, se podan las entradas cuya ventana ya venció en vez de
    // dejar crecer el mapa para siempre.
    private void limpiarMapaSiCrecioDemasiado() {
        if (intentosPorClave.size() > LIMITE_CLAVES_EN_MEMORIA) {
            long ahora = System.currentTimeMillis();
            intentosPorClave.entrySet().removeIf(e -> ahora - e.getValue().ventanaInicio > VENTANA_MS);
        }
    }

    // Emails y contraseñas que carga DataInitializer al primer arranque, cuando
    // la base está vacía. Se usan solo para detectar en el login si un usuario
    // sigue con la contraseña de fábrica (ver login() más abajo) — no se
    // guardan en ningún lado ni habilitan ningún acceso extra.
    private static final Map<String, String> PASSWORDS_DE_FABRICA = Map.of(
            "admin@miapp.com", "admin123",
            "cajero@miapp.com", "cajero123",
            "cocina@miapp.com", "cocina123",
            "mozo@miapp.com", "mozo123"
    );

    public void resetPasswordConClaveMaestra(String email, String claveMaestra, String nuevaPassword) {
        controlarIntentos("reset:" + email);
        // Comparación en tiempo constante para no filtrar información por timing
        boolean claveOk = claveMaestra != null && MessageDigest.isEqual(
                claveMaestra.getBytes(StandardCharsets.UTF_8),
                masterKey.getBytes(StandardCharsets.UTF_8));
        if (!claveOk) {
            registrarFallo("reset:" + email);
            throw new BusinessException("Clave maestra incorrecta");
        }
        limpiarIntentos("reset:" + email);
        if (email == null || email.isBlank()) {
            throw new BusinessException("Ingresá el email del usuario");
        }
        if (nuevaPassword == null || nuevaPassword.length() < 6) {
            throw new BusinessException("La contraseña debe tener al menos 6 caracteres");
        }
        Usuario usuario = usuarioRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BusinessException("No existe un usuario con ese email"));
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);
    }

    /**
     * Cambio de contraseña por el propio usuario ya logueado (no necesita la
     * clave maestra: alcanza con probar que conoce su contraseña actual).
     * Pensado sobre todo para el flujo de "estás usando la contraseña de
     * fábrica, poné una tuya" que dispara el panel después del login.
     */
    public void cambiarPasswordPropia(String email, String passwordActual, String passwordNueva) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("Sesión inválida");
        }
        controlarIntentos("cambio:" + email);
        Usuario usuario = usuarioRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BusinessException("Usuario no encontrado"));
        if (passwordActual == null || !passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            registrarFallo("cambio:" + email);
            throw new BusinessException("La contraseña actual no es correcta");
        }
        limpiarIntentos("cambio:" + email);
        if (passwordNueva == null || passwordNueva.length() < 6) {
            throw new BusinessException("La contraseña nueva debe tener al menos 6 caracteres");
        }
        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorId(Long id) {
        return toDTO(usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id)));
    }

    public UsuarioResponseDTO crear(UsuarioRequestDTO dto) {
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("Ya existe un usuario con el email: " + dto.getEmail());
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException("La contraseña es obligatoria para crear un usuario");
        }
        if (dto.getPassword().length() < 6) {
            throw new BusinessException("La contraseña debe tener al menos 6 caracteres");
        }
        Usuario usuario = Usuario.builder()
                .nombre(dto.getNombre())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .rol(dto.getRol())
                .build();
        return toDTO(usuarioRepository.save(usuario));
    }

    public UsuarioResponseDTO actualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        // Si cambia el email, verificar que no esté en uso
        if (!usuario.getEmail().equals(dto.getEmail())
                && usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("El email " + dto.getEmail() + " ya está en uso");
        }

        usuario.setNombre(dto.getNombre());
        usuario.setEmail(dto.getEmail());
        // Contraseña vacía o ausente = mantener la actual (permite editar nombre/rol sin tocarla)
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 6) {
                throw new BusinessException("La contraseña debe tener al menos 6 caracteres");
            }
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        usuario.setRol(dto.getRol());
        return toDTO(usuarioRepository.save(usuario));
    }

    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        // No permitir quedarse sin ningún administrador.
        if (usuario.getRol() == Rol.ADMIN && usuarioRepository.countByRol(Rol.ADMIN) <= 1) {
            throw new BusinessException("No se puede eliminar el único administrador del sistema.");
        }
        usuarioRepository.deleteById(id);
    }

    // Verifica email + contraseña. Devuelve el usuario solo si las credenciales son correctas.
    @Transactional(readOnly = true)
    public Optional<UsuarioResponseDTO> login(String email, String password) {
        if (email == null || password == null) return Optional.empty();
        // Freno de fuerza bruta, contado por email (ver comentario arriba de MAX_INTENTOS).
        controlarIntentos(email);
        Optional<UsuarioResponseDTO> res = usuarioRepository.findByEmail(email.trim())
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .map(u -> {
                    UsuarioResponseDTO dto = toDTO(u);
                    // ¿Sigue usando la contraseña de fábrica de este email? El panel
                    // usa este flag para obligarlo a cambiarla antes de seguir.
                    dto.setDebeCambiarPassword(password.equals(PASSWORDS_DE_FABRICA.get(u.getEmail())));
                    return dto;
                });
        if (res.isPresent()) { limpiarIntentos(email); } else { registrarFallo(email); }
        return res;
    }

    public UsuarioResponseDTO toDTO(Usuario u) {
        return UsuarioResponseDTO.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .email(u.getEmail())
                .rol(u.getRol())
                .build();
    }
}
