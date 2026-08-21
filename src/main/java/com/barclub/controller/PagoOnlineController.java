package com.barclub.controller;

import com.barclub.entity.Pedido;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.PedidoRepository;
import com.barclub.service.MercadoPagoService;
import com.barclub.websocket.RealtimeNotifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Pago online de un pedido con Mercado Pago (Checkout Pro). Todo opcional:
 * si no está configurado el Access Token, /pago-online/estado devuelve
 * habilitado=false y la web pública no ofrece esta alternativa — el cliente
 * sigue pagando al recibir, como siempre.
 *
 * Pagar online NO cambia el estado del pedido (PENDIENTE/PREPARACION/...):
 * solo queda registrado que la plata ya llegó (estadoPagoOnline), para que
 * el cajero no la vuelva a cobrar cuando el pedido se entrega.
 */
@Slf4j
@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pago online", description = "Pago de un pedido con Mercado Pago desde la web pública (opcional)")
public class PagoOnlineController {

    private final MercadoPagoService mercadoPagoService;
    private final PedidoRepository pedidoRepository;
    private final RealtimeNotifier realtimeNotifier;

    @GetMapping("/pago-online/estado")
    @Operation(summary = "¿Está habilitado el pago online en este local?")
    public ResponseEntity<Map<String, Boolean>> estado() {
        return ResponseEntity.ok(Map.of("habilitado", mercadoPagoService.isHabilitado()));
    }

    @PostMapping("/{id}/pago-online")
    @Operation(summary = "Iniciar el pago online de un pedido",
               description = "Crea el link de pago de Mercado Pago para el total de ese pedido y lo devuelve " +
                       "(el navegador del cliente tiene que redirigir ahí).")
    public ResponseEntity<Map<String, String>> iniciar(@PathVariable Long id) {
        if (!mercadoPagoService.isHabilitado()) {
            throw new BusinessException("El pago online no está disponible en este local por ahora.");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));

        try {
            Map<String, String> preferencia = mercadoPagoService.crearPreferencia(pedido);
            pedido.setMpPreferenceId(preferencia.get("preferenceId"));
            pedido.setEstadoPagoOnline("PENDIENTE");
            pedidoRepository.save(pedido);
            return ResponseEntity.ok(Map.of("initPoint", preferencia.getOrDefault("initPoint", "")));
        } catch (Exception e) {
            log.warn("No se pudo crear la preferencia de pago para el pedido {}: {}", id, e.getMessage());
            throw new BusinessException("No se pudo iniciar el pago online. Probá de nuevo en un momento.");
        }
    }

    @PostMapping("/pago-online/webhook")
    @Operation(summary = "Webhook de Mercado Pago",
               description = "Lo llama Mercado Pago (no el panel ni la web pública) para avisar que cambió el " +
                       "estado de un pago. Siempre reconsulta el pago directamente a Mercado Pago antes de dar " +
                       "por buena la notificación, en vez de confiar en los datos que vengan acá.")
    public ResponseEntity<Void> webhook(@RequestParam Map<String, String> params) {
        // Mercado Pago manda el id del pago como query param "data.id" (formato
        // nuevo de webhooks) — se ignora cualquier otro tipo de aviso (por
        // ejemplo "merchant_order") porque a nosotros solo nos interesa "payment".
        String tipo = params.get("type");
        String paymentId = params.get("data.id");
        if (paymentId == null) paymentId = params.get("id");
        if (paymentId == null || (tipo != null && !"payment".equals(tipo))) {
            return ResponseEntity.ok().build();
        }

        try {
            Map<String, Object> pago = mercadoPagoService.consultarPago(paymentId);
            String externalRef = (String) pago.get("externalReference");
            String status = (String) pago.get("status");
            if (externalRef == null) return ResponseEntity.ok().build();

            Long pedidoId = Long.parseLong(externalRef);
            final String estadoFinal = mapearEstado(status);
            pedidoRepository.findById(pedidoId).ifPresent(pedido -> {
                pedido.setMpPaymentId(paymentId);
                pedido.setEstadoPagoOnline(estadoFinal);
                pedidoRepository.save(pedido);
                realtimeNotifier.avisarPedidos();
            });
        } catch (Exception e) {
            // No relanzamos: si esto tira 500, Mercado Pago reintenta el mismo
            // aviso varias veces durante horas. Devolver 200 igual evita esos
            // reintentos por, por ejemplo, un pedido que ya no existe — el log
            // deja rastro para revisar a mano si hace falta.
            log.warn("Error procesando webhook de Mercado Pago (paymentId={}): {}", paymentId, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    private String mapearEstado(String mpStatus) {
        if ("approved".equals(mpStatus)) return "APROBADO";
        if ("rejected".equals(mpStatus) || "cancelled".equals(mpStatus)) return "RECHAZADO";
        return "PENDIENTE";
    }
}
