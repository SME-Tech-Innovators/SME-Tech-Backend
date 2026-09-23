package sme.tech.innovators.sme.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sme.tech.innovators.sme.config.BobGoConfig;
import sme.tech.innovators.sme.entity.OrderShipment;
import sme.tech.innovators.sme.exception.BobGoWebhookInvalidException;
import sme.tech.innovators.sme.repository.OrderShipmentRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BobGoWebhookService {

    private final BobGoConfig bobGoConfig;
    private final OrderShipmentRepository orderShipmentRepository;
    private final ObjectMapper objectMapper;
    private final BobGoShipmentStatusService shipmentStatusService;

    public void handleWebhook(String signatureHeader, String rawBody) {
        verifySignature(signatureHeader, rawBody);
        Map<String, Object> payload = parsePayload(rawBody);
        applyFulfillmentEvent(payload);
    }

    protected void applyFulfillmentEvent(Map<String, Object> payload) {
        Map<String, Object> root = unwrapData(payload);
        String shipmentId = firstNonBlank(
                stringVal(root.get("shipment_id")),
                stringVal(root.get("shipmentId")),
                stringVal(root.get("id")));
        String tracking = firstNonBlank(
                stringVal(root.get("tracking_reference")),
                stringVal(root.get("trackingReference")),
                stringVal(root.get("tracking_number")),
                stringVal(root.get("trackingNumber")),
                stringVal(root.get("awb")));

        if (shipmentId == null && tracking == null) {
            log.info("Bob Go webhook ignored: no shipment id or tracking in payload");
            return;
        }

        Optional<OrderShipment> shipmentOpt = Optional.empty();
        if (shipmentId != null) {
            shipmentOpt = orderShipmentRepository.findByBobgoShipmentId(shipmentId);
        }
        if (shipmentOpt.isEmpty() && tracking != null) {
            shipmentOpt = orderShipmentRepository.findByTrackingReference(tracking);
        }
        if (shipmentOpt.isEmpty()) {
            log.info("Bob Go webhook: no local shipment for bobgoId={} tracking={}", shipmentId, tracking);
            return;
        }

        OrderShipment shipment = shipmentOpt.get();
        shipmentStatusService.apply(shipment.getWorkspaceId(), shipment.getOrder().getId(), payload);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(Map<String, Object> payload) {
        if (payload.get("data") instanceof Map<?, ?> data) {
            return (Map<String, Object>) data;
        }
        if (payload.get("fulfillment") instanceof Map<?, ?> fulfillment) {
            return (Map<String, Object>) fulfillment;
        }
        return payload;
    }

    private Map<String, Object> parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new BobGoWebhookInvalidException("Invalid webhook payload");
        }
    }

    private void verifySignature(String signatureHeader, String rawBody) {
        String secret = bobGoConfig.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BobGoWebhookInvalidException("Webhook secret is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BobGoWebhookInvalidException("Missing bobgo-webhook-signature");
        }
        String header = signatureHeader.trim();
        if (timingSafeEquals(header, secret)) {
            return;
        }
        String computedHex = hmacSha256Hex(rawBody, secret);
        if (matchesDigest(header, computedHex)) {
            return;
        }
        if (verifyTimestamped(header, rawBody, secret)) {
            return;
        }
        throw new BobGoWebhookInvalidException("Invalid webhook signature");
    }

    private static boolean verifyTimestamped(String header, String rawBody, String secret) {
        if (!header.contains("v1=") || !header.contains("t=")) {
            return false;
        }
        String t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                t = kv[1];
            } else if ("v1".equals(kv[0])) {
                v1 = kv[1];
            }
        }
        if (t == null || v1 == null) {
            return false;
        }
        String expected = hmacSha256Hex(t + "." + rawBody, secret);
        return timingSafeEquals(v1, expected);
    }

    private static boolean matchesDigest(String header, String computedHex) {
        if (timingSafeEquals(header, computedHex)) {
            return true;
        }
        if (header.regionMatches(true, 0, "sha256=", 0, 7)) {
            return timingSafeEquals(header.substring(7).trim(), computedHex);
        }
        return false;
    }

    private static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new BobGoWebhookInvalidException("Failed to verify webhook signature");
        }
    }

    private static boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
