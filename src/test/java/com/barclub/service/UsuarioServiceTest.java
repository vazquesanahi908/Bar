package com.barclub.service;

import com.barclub.entity.Rol;
import com.barclub.entity.Usuario;
import com.barclub.exception.BusinessException;
import com.barclub.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Cubre el login (incluida la protección contra fuerza bruta y el aviso de
 * "seguís con la contraseña de fábrica") y el cambio de contraseña propia.
 * Antes no había ningún test de este servicio, a pesar de ser el punto de
 * entrada de autenticación de todo el sistema.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario admin;

    @BeforeEach
    void setUp() {
        admin = Usuario.builder()
                .id(1L)
                .nombre("Admin Principal")
                .email("admin@miapp.com")
                .password("HASH_DE_admin123")
                .rol(Rol.ADMIN)
                .build();
        ReflectionTestUtils.setField(usuarioService, "masterKey", "admin2026");
    }

    // -------------------------------------------------------
    // TEST 1: Login correcto con la contraseña de fábrica del admin
    // debe avisar (debeCambiarPassword=true) pero igual dejar entrar.
    // -------------------------------------------------------
    @Test
    void login_conPasswordDeFabrica_debeAvisarQueHayQueCambiarla() {
        when(usuarioRepository.findByEmail("admin@miapp.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin123", admin.getPassword())).thenReturn(true);

        Optional<com.barclub.dto.UsuarioResponseDTO> res = usuarioService.login("admin@miapp.com", "admin123");

        assertTrue(res.isPresent());
        assertTrue(res.get().isDebeCambiarPassword());
    }

    // -------------------------------------------------------
    // TEST 2: Login correcto con una contraseña YA cambiada no debe avisar.
    // -------------------------------------------------------
    @Test
    void login_conPasswordYaCambiada_noDebeAvisar() {
        when(usuarioRepository.findByEmail("admin@miapp.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("miClaveNueva99", admin.getPassword())).thenReturn(true);

        Optional<com.barclub.dto.UsuarioResponseDTO> res = usuarioService.login("admin@miapp.com", "miClaveNueva99");

        assertTrue(res.isPresent());
        assertFalse(res.get().isDebeCambiarPassword());
    }

    // -------------------------------------------------------
    // TEST 3: Contraseña incorrecta — no deja entrar.
    // -------------------------------------------------------
    @Test
    void login_passwordIncorrecta_debeRetornarVacio() {
        when(usuarioRepository.findByEmail("admin@miapp.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("cualquiercosa", admin.getPassword())).thenReturn(false);

        Optional<com.barclub.dto.UsuarioResponseDTO> res = usuarioService.login("admin@miapp.com", "cualquiercosa");

        assertTrue(res.isEmpty());
    }

    // -------------------------------------------------------
    // TEST 4: Fuerza bruta — después de MAX_INTENTOS (5) fallos seguidos
    // contra el MISMO email, el 6to intento se bloquea aunque la
    // contraseña sea correcta.
    // -------------------------------------------------------
    @Test
    void login_masDe5IntentosFallidosSeguidos_debeBloquearseTemporalmente() {
        when(usuarioRepository.findByEmail("admin@miapp.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("mala", admin.getPassword())).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertTrue(usuarioService.login("admin@miapp.com", "mala").isEmpty());
        }

        // El 6to intento, aunque ahora se mande la contraseña correcta,
        // debe rechazarse por el freno de intentos (no llega a validarla).
        assertThrows(BusinessException.class, () -> usuarioService.login("admin@miapp.com", "admin123"));
    }

    // -------------------------------------------------------
    // TEST 5: Fuerza bruta — el bloqueo es POR EMAIL, no global: fallar el
    // login de un usuario no debe afectar el login de otro.
    // -------------------------------------------------------
    @Test
    void login_intentosFallidosDeUnUsuario_noDebenBloquearAOtroUsuario() {
        Usuario cajero = Usuario.builder()
                .id(2L).nombre("Cajero").email("cajero@miapp.com")
                .password("HASH_DE_cajero123").rol(Rol.CAJERO).build();

        when(usuarioRepository.findByEmail("admin@miapp.com")).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByEmail("cajero@miapp.com")).thenReturn(Optional.of(cajero));
        when(passwordEncoder.matches("mala", admin.getPassword())).thenReturn(false);
        when(passwordEncoder.matches("cajero123", cajero.getPassword())).thenReturn(true);

        // Agota el límite de intentos del admin (5 fallos seguidos).
        for (int i = 0; i < 5; i++) {
            usuarioService.login("admin@miapp.com", "mala");
        }
        // Confirmado: el admin queda bloqueado.
        assertThrows(BusinessException.class, () -> usuarioService.login("admin@miapp.com", "mala"));

        // El cajero (otra clave en el mapa de intentos) debe poder entrar sin problema.
        Optional<com.barclub.dto.UsuarioResponseDTO> res = usuarioService.login("cajero@miapp.com", "cajero123");
        assertTrue(res.isPresent());
    }

    // -------------------------------------------------------
    // TEST 6: Cambiar la propia contraseña con la actual correcta.
    // -------------------------------------------------------
    @Test
    void cambiarPasswordPropia_actualCorrecta_debeActualizarla() {
        when(usuarioRepository.findByEmail("admin@miapp.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin123", admin.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("nuevaSegura1")).thenReturn("HASH_DE_nuevaSegura1");

        usuarioService.cambiarPasswordPropia("admin@miapp.com", "admin123", "nuevaSegura1");

        assertEquals("HASH_DE_nuevaSegura1", admin.getPassword());
        verify(usuarioRepository).save(admin);
    }

    // -------------------------------------------------------
    // TEST 7: Cambiar la propia contraseña con la actual incorrecta — falla.
    // -------------------------------------------------------
    @Test
    void cambiarPasswordPropia_actualIncorrecta_debeLanzarBusinessException() {
        when(usuarioRepository.findByEmail("admin@miapp.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("noEsLaActual", admin.getPassword())).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> usuarioService.cambiarPasswordPropia("admin@miapp.com", "noEsLaActual", "nuevaSegura1"));
        verify(usuarioRepository, never()).save(any());
    }

    // -------------------------------------------------------
    // TEST 8: Nueva contraseña demasiado corta — falla incluso con la
    // actual correcta.
    // -------------------------------------------------------
    @Test
    void cambiarPasswordPropia_nuevaMuyCorta_debeLanzarBusinessException() {
        when(usuarioRepository.findByEmail("admin@miapp.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin123", admin.getPassword())).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> usuarioService.cambiarPasswordPropia("admin@miapp.com", "admin123", "abc"));
        verify(usuarioRepository, never()).save(any());
    }
}
