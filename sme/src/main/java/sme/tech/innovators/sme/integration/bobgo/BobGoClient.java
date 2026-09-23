package sme.tech.innovators.sme.integration.bobgo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import sme.tech.innovators.sme.config.BobGoConfig;
import sme.tech.innovators.sme.exception.ShippingQuoteFailedException;
import sme.tech.innovators.sme.exception.ShipmentCreateFailedException;

import java.util.Map;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BobGoClient {

    private final RestClient bobGoRestClient;
    private final BobGoConfig bobGoConfig;

    public Map<String, Object> postRates(Map<String, Object> body) {
        return post("/rates", body, "Bob Go rates request failed");
    }

    public Map<String, Object> postShipments(Map<String, Object> body) {
        try {
            return post("/shipments", body, "Bob Go shipment creation failed");
        } catch (ShippingQuoteFailedException ex) {
            throw new ShipmentCreateFailedException(ex.getMessage());
        }
    }

    public Map<String, Object> cancelShipment(String trackingReference) {
        return post("/shipments/cancel", Map.of("tracking_reference", trackingReference),
                "Bob Go cancellation could not be confirmed");
    }

    public Map<String, Object> getTracking(String trackingReference) {
        try {
            Object response = bobGoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tracking")
                            .queryParam("tracking_reference", trackingReference)
                            .build())
                    .headers(this::authHeaders)
                    .retrieve()
                    .body(Object.class);
            return trackingResult(response, trackingReference);
        } catch (RestClientException ex) {
            log.warn("Bob Go tracking failed ref={} error={}", trackingReference, ex.getClass().getSimpleName());
            throw new ShipmentCreateFailedException("Failed to fetch Bob Go tracking");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> trackingResult(Object response, String trackingReference) {
        // Tracking returns an array; retain support for object responses.
        if (response instanceof Map<?, ?> map) return (Map<String, Object>) map;
        if (response instanceof List<?> results) {
            if (results.isEmpty()) return Map.of();
            for (Object result : results) {
                if (result instanceof Map<?, ?> map
                        && trackingReference.equals(map.get("shipment_tracking_reference"))) {
                    return (Map<String, Object>) map;
                }
            }
        }
        throw new ShipmentCreateFailedException("Bob Go tracking response did not contain the requested shipment");
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String errorPrefix) {
        try {
            return bobGoRestClient.post()
                    .uri(path)
                    .headers(this::authHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientResponseException ex) {
            log.warn("Bob Go {} failed status={} body={}", path, ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            throw new ShippingQuoteFailedException(errorPrefix + ": " + safeMessage(ex));
        } catch (RuntimeException ex) {
            throw new ShippingQuoteFailedException(errorPrefix);
        }
    }

    private void authHeaders(HttpHeaders headers) {
        String token = bobGoConfig.getBearerToken();
        if (token == null || token.isBlank()) {
            throw new ShippingQuoteFailedException("Bob Go API token is not configured");
        }
        headers.setBearerAuth(token.trim());
    }

    private static String safeMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body != null && body.length() > 200) {
            return body.substring(0, 200);
        }
        return body != null ? body : ex.getMessage();
    }
}
