package sme.tech.innovators.sme.integration.uberdirect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import sme.tech.innovators.sme.config.UberDirectProperties;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Low-level HTTP client for the Uber Direct (Courier) API.
 *
 * <p>Supports a {@code mockMode} flag ({@code app.uber-direct.mock-mode=true})
 * that returns realistic canned responses without hitting the Uber API.
 * Useful for demos, local dev without a verified Uber account, or academic
 * projects where sandbox onboarding (tax form) has not been completed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UberDirectClient {

    private final UberDirectProperties props;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    // ── Token cache ───────────────────────────────────────────────────────

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    private final ReentrantLock tokenLock = new ReentrantLock();

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Request a delivery quote (price + ETA) from Uber Direct.
     * Returns a mock response when {@code app.uber-direct.mock-mode=true}.
     */
    public JsonNode createQuote(Map<String, Object> pickup,
                                Map<String, Object> dropoff,
                                int manifestTotalValueCents) {
        if (props.isMockMode()) {
            log.info("UberDirect [MOCK] createQuote — returning simulated quote");
            return mockQuoteResponse();
        }

        requireConfigured();
        String token = getAccessToken();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pickup_address", toAddressString(pickup));
        body.put("pickup_latitude", pickup.get("latitude"));
        body.put("pickup_longitude", pickup.get("longitude"));
        body.put("pickup_phone_number", pickup.get("phone_number"));
        body.put("dropoff_address", toAddressString(dropoff));
        body.put("dropoff_latitude", dropoff.get("latitude"));
        body.put("dropoff_longitude", dropoff.get("longitude"));
        body.put("dropoff_phone_number", dropoff.get("phone_number"));
        body.put("manifest_total_value", manifestTotalValueCents);
        body.values().removeIf(v -> v == null);

        String uri = "/v1/customers/" + props.getCustomerId() + "/delivery_quotes";
        log.debug("UberDirect createQuote POST {}", uri);

        try {
            return restClientBuilder
                    .baseUrl(props.getBaseUrl())
                    .build()
                    .post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            log.warn("UberDirect quote client error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Uber Direct quote failed: " + e.getMessage());
        } catch (HttpServerErrorException e) {
            log.error("UberDirect quote server error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Uber Direct is temporarily unavailable");
        } catch (Exception e) {
            log.error("UberDirect quote unexpected error", e);
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Could not retrieve Uber Direct quote");
        }
    }

    /**
     * Book a confirmed on-demand delivery with Uber Direct.
     * Returns a mock response when {@code app.uber-direct.mock-mode=true}.
     */
    public JsonNode createDelivery(String quoteId,
                                   Map<String, Object> pickup,
                                   Map<String, Object> dropoff,
                                   String manifest,
                                   int manifestTotalValueCents) {
        if (props.isMockMode()) {
            log.info("UberDirect [MOCK] createDelivery — returning simulated delivery");
            return mockDeliveryResponse(quoteId);
        }

        requireConfigured();
        String token = getAccessToken();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quote_id", quoteId);
        body.put("pickup_name", pickup.get("name"));
        body.put("pickup_address", toAddressString(pickup));
        body.put("pickup_latitude", pickup.get("latitude"));
        body.put("pickup_longitude", pickup.get("longitude"));
        body.put("pickup_phone_number", pickup.get("phone_number"));
        body.put("pickup_instructions", pickup.get("instructions"));
        body.put("dropoff_name", dropoff.get("name"));
        body.put("dropoff_address", toAddressString(dropoff));
        body.put("dropoff_latitude", dropoff.get("latitude"));
        body.put("dropoff_longitude", dropoff.get("longitude"));
        body.put("dropoff_phone_number", dropoff.get("phone_number"));
        body.put("dropoff_instructions", dropoff.get("instructions"));
        body.put("manifest_items", java.util.List.of(
                Map.of("name", manifest, "quantity", 1, "price", manifestTotalValueCents)
        ));
        body.put("manifest_total_value", manifestTotalValueCents);
        body.values().removeIf(v -> v == null);

        String uri = "/v1/customers/" + props.getCustomerId() + "/deliveries";
        log.debug("UberDirect createDelivery POST {}", uri);

        try {
            return restClientBuilder
                    .baseUrl(props.getBaseUrl())
                    .build()
                    .post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            log.warn("UberDirect createDelivery client error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Uber Direct delivery creation failed: " + e.getMessage());
        } catch (HttpServerErrorException e) {
            log.error("UberDirect createDelivery server error", e);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Uber Direct is temporarily unavailable");
        } catch (Exception e) {
            log.error("UberDirect createDelivery unexpected error", e);
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Could not create Uber Direct delivery");
        }
    }

    /**
     * Fetch the current status of a delivery.
     * Returns a mock response when {@code app.uber-direct.mock-mode=true}.
     */
    public JsonNode getDelivery(String deliveryId) {
        if (props.isMockMode()) {
            log.info("UberDirect [MOCK] getDelivery id={} — returning simulated status", deliveryId);
            return mockDeliveryStatusResponse(deliveryId);
        }

        requireConfigured();
        String token = getAccessToken();

        String uri = "/v1/customers/" + props.getCustomerId() + "/deliveries/" + deliveryId;
        log.debug("UberDirect getDelivery GET {}", uri);

        try {
            return restClientBuilder
                    .baseUrl(props.getBaseUrl())
                    .build()
                    .get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            log.warn("UberDirect getDelivery client error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Uber Direct status check failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("UberDirect getDelivery unexpected error", e);
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Could not fetch Uber Direct delivery status");
        }
    }

    // ── Mock responses ────────────────────────────────────────────────────

    /**
     * Simulates a successful Uber Direct quote response.
     * Fee is R55.00 (5500 cents), ETA pickup 8 min / dropoff 25 min.
     */
    private JsonNode mockQuoteResponse() {
        String quoteId = "mock_quote_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String expires = Instant.now().plusSeconds(300)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", quoteId);
        node.put("kind", "delivery_quote");
        node.put("fee", 5500);          // ZAR cents → R55.00
        node.put("currency_code", "ZAR");
        node.put("pickup_eta", 8);      // minutes
        node.put("dropoff_eta", 25);    // minutes
        node.put("expires", expires);
        node.put("duration", 1020);     // seconds (17 min)
        node.putObject("pickup").put("name", "Mock Pickup");
        node.putObject("dropoff").put("name", "Mock Dropoff");
        log.debug("UberDirect [MOCK] quote id={} expires={}", quoteId, expires);
        return node;
    }

    /**
     * Simulates a successful Uber Direct delivery creation response.
     */
    private JsonNode mockDeliveryResponse(String quoteId) {
        String deliveryId = "mock_del_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", deliveryId);
        node.put("quote_id", quoteId != null ? quoteId : "mock_quote_none");
        node.put("status", "pending");
        node.put("tracking_url",
                "https://www.uber.com/track/" + deliveryId + "?mock=true");

        ObjectNode courier = node.putObject("courier");
        courier.put("name", "Sipho M.");
        courier.put("phone", "+27600000001");
        courier.put("vehicle_type", "motorbike");

        node.put("pickup_eta", 8);
        node.put("dropoff_eta", 25);

        ObjectNode pickup = node.putObject("pickup");
        pickup.put("address", "128 New Road, Midrand, Gauteng 1685, ZA");
        pickup.put("name", "Store Manager");

        ObjectNode dropoff = node.putObject("dropoff");
        dropoff.put("address", "Kyalami Corner, Midrand, Gauteng 1684, ZA");

        log.debug("UberDirect [MOCK] delivery id={}", deliveryId);
        return node;
    }

    /**
     * Simulates a delivery status poll response.
     * Returns {@code pickup} status (courier en route to collect).
     */
    private JsonNode mockDeliveryStatusResponse(String deliveryId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", deliveryId);
        node.put("status", "pickup");
        node.put("tracking_url",
                "https://www.uber.com/track/" + deliveryId + "?mock=true");

        ObjectNode courier = node.putObject("courier");
        courier.put("name", "Sipho M.");
        courier.put("phone", "+27600000001");
        courier.put("vehicle_type", "motorbike");
        courier.put("latitude", -25.9950);
        courier.put("longitude", 28.1100);

        node.put("pickup_eta", 3);
        node.put("dropoff_eta", 20);
        return node;
    }

    // ── OAuth2 token ──────────────────────────────────────────────────────

    String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        tokenLock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
                return cachedToken;
            }
            log.info("UberDirect: fetching new access token");
            fetchAndCacheToken();
            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }

    private void fetchAndCacheToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("grant_type", "client_credentials");
        form.add("scope", "eats.deliveries");

        try {
            JsonNode response = restClientBuilder
                    .baseUrl(props.getTokenUrl())
                    .build()
                    .post()
                    .uri("")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || response.path("access_token").isMissingNode()) {
                throw new ResponseStatusException(BAD_GATEWAY,
                        "Uber Direct token response was empty or malformed");
            }

            cachedToken = response.path("access_token").asText();
            long expiresIn = response.path("expires_in").asLong(3600);
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
            log.info("UberDirect: access token cached, expires in {}s", expiresIn);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("UberDirect: token fetch failed", e);
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Could not authenticate with Uber Direct");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void requireConfigured() {
        if (!props.isConfigured() && !props.isMockMode()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Uber Direct is not configured on this platform");
        }
    }

    private String toAddressString(Map<String, Object> addr) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, addr, "street_address");
        appendIfPresent(sb, addr, "city");
        appendIfPresent(sb, addr, "state");
        appendIfPresent(sb, addr, "zip_code");
        appendIfPresent(sb, addr, "country");
        return sb.toString().trim().replaceAll(",\\s*$", "");
    }

    private void appendIfPresent(StringBuilder sb, Map<String, Object> addr, String key) {
        Object val = addr.get(key);
        if (val != null && !val.toString().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(val.toString().trim());
        }
    }
}
