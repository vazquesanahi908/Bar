package com.barclub.controller;

import com.barclub.websocket.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint de solo prueba para el canal de tiempo real (WebSocket). Dispara
 * un aviso de "reservas" al toque, para poder confirmar que el camino
 * completo (backend → WebSocket → panel) funciona con un solo botón, sin
 * necesitar una segunda sesión ni otro dispositivo para probarlo.
 *
 * No expone ni modifica ningún dato real — solo reenvía la misma señal
 * vacía que ya se manda cuando entra una reserva de verdad (ver
 * RealtimeNotifier). Se puede borrar este archivo sin ningún efecto
 * colateral una vez que quede confirmado que el tiempo real funciona.
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevTestController {

    private final RealtimeNotifier realtimeNotifier;

    @GetMapping("/test-realtime")
    public Map<String, Object> testRealtime() {
        realtimeNotifier.avisarReservas();
        return Map.of("ok", true, "mensaje", "Aviso de reservas enviado por WebSocket");
    }
}
