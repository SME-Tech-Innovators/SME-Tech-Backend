package sme.tech.innovators.sme.integration.paystack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import sme.tech.innovators.sme.config.PaystackConfig;
import sme.tech.innovators.sme.exception.InvalidBankAccountException;
import sme.tech.innovators.sme.exception.PaymentInitializationFailedException;
import sme.tech.innovators.sme.exception.PaystackSubaccountFailedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaystackClient {

    private final RestClient paystackRestClient;
    private final PaystackConfig paystackConfig;
    private final ObjectMapper objectMapper;

    public Map<String, Object> createSubaccount(String businessName,
                                                String settlementBank,
                                                String accountNumber,
                                                int percentageCharge) {
        Map<String, Object> body = new HashMap<>();
        body.put("business_name", businessName);
        body.put("settlement_bank", settlementBank);
        body.put("account_number", accountNumber);
        body.put("percentage_charge", percentageCharge);
        return post("/subaccount", body, true);
    }

    public Map<String, Object> updateSubaccount(String subaccountCode,
                                                 String businessName,
                                                 String settlementBank,
                                                 String accountNumber,
                                                 int percentageCharge) {
        Map<String, Object> body = new HashMap<>();
        body.put("business_name", businessName);
        body.put("settlement_bank", settlementBank);
        body.put("account_number", accountNumber);
        body.put("percentage_charge", percentageCharge);
        return put("/subaccount/" + subaccountCode, body, true);
    }

    public Map<String, Object> initializeTransaction(String email,
                                                      int amount,
                                                      String currency,
                                                      String reference,
                                                      String callbackUrl,
                                                      String subaccountCode,
                                                      Map<String, Object> metadata) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("reference", reference);
        body.put("subaccount", subaccountCode);
        body.put("metadata", metadata);
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            body.put("callback_url", callbackUrl);
        }
        return post("/transaction/initialize", body, false);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listBanks(String country) {
        String code = country == null || country.isBlank() ? "ZA" : country.trim().toUpperCase();
        try {
            ResponseEntity<Map> response = paystackRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/bank")
                            .queryParam("country", code)
                            .build())
                    .retrieve()
                    .toEntity(Map.class);
            Map<String, Object> payload = response.getBody();
            if (payload == null || !Boolean.TRUE.equals(payload.get("status"))) {
                throw new InvalidBankAccountException("Failed to load banks from Paystack");
            }
            Object data = payload.get("data");
            if (!(data instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        } catch (RestClientResponseException ex) {
            log.warn("Paystack list banks failed: {}", ex.getResponseBodyAsString());
            throw new InvalidBankAccountException("Failed to load banks from Paystack");
        }
    }

    public Map<String, Object> verifyTransaction(String reference) {
        try {
            ResponseEntity<Map> response = paystackRestClient.get()
                    .uri("/transaction/verify/" + reference)
                    .retrieve()
                    .toEntity(Map.class);
            return requireSuccess(response.getBody(), false);
        } catch (RestClientResponseException ex) {
            log.warn("Paystack verify failed for {}: {}", reference, ex.getResponseBodyAsString());
            throw new PaymentInitializationFailedException(
                    extractMessage(ex.getResponseBodyAsString()));
        }
    }

    public String getPublicKey() {
        return paystackConfig.getPublicKey();
    }

    public int getPlatformFeePercent() {
        return paystackConfig.getPlatformFeePercent();
    }

    public String getWebhookSecret() {
        return paystackConfig.getWebhookSecret();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body, boolean subaccount) {
        try {
            ResponseEntity<Map> response = paystackRestClient.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
            return requireSuccess(response.getBody(), subaccount);
        } catch (RestClientResponseException ex) {
            log.warn("Paystack POST {} failed: {}", path, ex.getResponseBodyAsString());
            throw wrap(ex, subaccount);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> put(String path, Map<String, Object> body, boolean subaccount) {
        try {
            ResponseEntity<Map> response = paystackRestClient.put()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
            return requireSuccess(response.getBody(), subaccount);
        } catch (RestClientResponseException ex) {
            log.warn("Paystack PUT {} failed: {}", path, ex.getResponseBodyAsString());
            throw wrap(ex, subaccount);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireSuccess(Map<String, Object> payload, boolean subaccount) {
        if (payload == null || !Boolean.TRUE.equals(payload.get("status"))) {
            String message = payload != null && payload.get("message") != null
                    ? payload.get("message").toString()
                    : "Paystack request failed";
            if (subaccount) {
                throw new PaystackSubaccountFailedException(message);
            }
            throw new PaymentInitializationFailedException(message);
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<>() {});
        }
        return Map.of();
    }

    private RuntimeException wrap(RestClientResponseException ex, boolean subaccount) {
        String message = extractMessage(ex.getResponseBodyAsString());
        if (subaccount) {
            return new PaystackSubaccountFailedException(message);
        }
        return new PaymentInitializationFailedException(message);
    }

    private String extractMessage(String body) {
        try {
            Map<String, Object> map = objectMapper.readValue(body, new TypeReference<>() {});
            if (map.get("message") != null) {
                return map.get("message").toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "Paystack request failed";
    }
}
