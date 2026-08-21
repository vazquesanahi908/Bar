package com.barclub.service;

import com.barclub.entity.Pedido;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Llamadas directas a la API REST de Mercado Pago (Checkout Pro), sin el SDK
 * oficial — se implementa con java.net.http.HttpClient (viene con Java, no
 * suma ninguna dependencia nueva al proyecto) más el ObjectMapper de Jackson
 * que ya usa el resto de la app.
 *
 * Dos llamadas nada más:
 *  - crearPreferencia: arma el "link de pago" para un pedido puntual.
 *  - consultarPago: dado un ID de pago que avisó el webhook, le pregunta a
 *    Mercado Pago directamente cuál es el estado REAL de ese pago — nunca se
 *    confía en lo que venga en la notificación en sí, que se puede falsificar
 *    (así lo recomienda la propia documentación de Mercado Pago).
 */
@Slf4j
@Service
public class MercadoPagoService {

    private static final String BASE_URL = "https://api.mercadopago.com";

    @Value("${app.mercadopago.habilitado:false}")
    private boolean habilitado;

    @Value("${app.mercadopago.access-token:}")
    private String accessToken;

    @Value("${app.mercadopago.notificacion-url:}")
    private String notificacionUrl;

    @Value("${app.mercadopago.front-url:}")
    private String frontUrl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** true solo si está prendido Y tiene el Access Token cargado. */
    public boolean isHabilitado() {
        return habilitado && accessToken != null && !accessToken.isBlank();
    }

    /**
     * Crea una preferencia de pago en Mercado Pago para el total de un
     * pedido puntual. Devuelve el id de la preferencia y el link
     * (init_point) al que hay que mandar al cliente para que pague.
     */
    public Map<String, String> crearPreferencia(Pedido pedido) throws Exception {
        ObjectNode body = mapper.createObjectNode();

        ObjectNode item = mapper.createObjectNode();
        item.put("title", "Pedido #" + pedido.getId());
        item.put("quantity", 1);
        item.put("unit_price", pedido.getTotal() != null ? pedido.getTotal() : 0.0);
        item.put("currency_id", "ARS");
        body.putArray("items").add(item);

        // Así sabemos, cuando llegue el aviso de pago, a qué pedido corresponde.
        body.put("external_reference", String.valueOf(pedido.getId()));

        if (frontUrl != null && !frontUrl.isBlank()) {
            ObjectNode backUrls = mapper.createObjectNode();
            backUrls.put("success", frontUrl + "?pago=exito&pedido=" + pedido.getId());
            backUrls.put("failure", frontUrl + "?pago=error&pedido=" + pedido.getId());
            backUrls.put("pending", frontUrl + "?pago=pendiente&pedido=" + pedido.getId());
            body.set("back_urls", backUrls);
            body.put("auto_return", "approved");
        }

        if (notificacionUrl != null && !notificacionUrl.isBlank()) {
            body.put("notification_url", notificacionUrl + "/api/pedidos/pago-online/webhook");
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/checkout/preferences"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            log.warn("Mercado Pago devolvió {} al crear una preferencia: {}", res.statusCode(), res.body());
            throw new IllegalStateException("Mercado Pago rechazó la solicitud (código " + res.statusCode() + ")");
        }

        JsonNode json = mapper.readTree(res.body());
        Map<String, String> resultado = new HashMap<>();
        resultado.put("preferenceId", json.path("id").asText(null));
        resultado.put("initPoint", json.path("init_point").asText(null));
        return resultado;
    }

    /**
     * Le pregunta a Mercado Pago el estado real de un pago (nunca se confía
     * en el estado que venga dentro de la notificación del webhook).
     * Devuelve al menos "status" y "external_reference".
     */
    public Map<String, Object> consultarPago(String paymentId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/v1/payments/" + paymentId))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            log.warn("Mercado Pago devolvió {} al consultar el pago {}: {}", res.statusCode(), paymentId, res.body());
            throw new IllegalStateException("No se pudo consultar el pago (código " + res.statusCode() + ")");
        }

        JsonNode json = mapper.readTree(res.body());
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("status", json.path("status").asText(null));
        resultado.put("externalReference", json.path("external_reference").asText(null));
        return resultado;
    }
}
