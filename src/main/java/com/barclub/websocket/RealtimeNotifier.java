package com.barclub.websocket;

import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Punto único desde el que los services avisan "esto cambió" a todos los
 * paneles conectados. Deliberadamente NO manda los datos en sí (ver
 * WebSocketConfig) — solo el tipo de evento y una marca de tiempo, para que
 * el panel sepa qué volver a pedir por la API normal.
 *
 * Si el envío falla por lo que sea (por ejemplo, si en algún entorno el
 * WebSocket no está disponible), se ignora en silencio: el polling de
 * respaldo del panel se encarga igual, así que un fallo acá nunca debe
 * interrumpir la operación real (crear un pedido, cobrar, reservar, etc.).
 */
@Component
@RequiredArgsConstructor
public class RealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);

    private final SimpMessagingTemplate template;

    public void avisarPedidos() {
        enviar("/topic/pedidos", "pedidos");
    }

    public void avisarReservas() {
        enviar("/topic/reservas", "reservas");
    }

    private void enviar(String destino, String tipo) {
        try {
            template.convertAndSend(destino, Map.of("tipo", tipo, "en", Instant.now().toString()));
        } catch (Exception e) {
            log.warn("No se pudo enviar el aviso en tiempo real de '{}' (el polling de respaldo lo cubre igual): {}", tipo, e.getMessage());
        }
    }
}
