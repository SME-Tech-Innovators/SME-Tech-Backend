package sme.tech.innovators.sme.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import sme.tech.innovators.sme.config.ShipLogicProperties;
import sme.tech.innovators.sme.dto.request.DeliveryQuoteRequest;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipLogicQuoteService {

    private final ShipLogicProperties shipLogicProperties;
    private final RestClient.Builder restClientBuilder;
    private final WorkspaceRepository workspaceRepository;

    public JsonNode getQuote(
            UUID workspaceId,
            UUID userId,
            DeliveryQuoteRequest request
    ) {
            log.info("########## SHIPLOGIC QUOTE START ##########");
            log.info("workspaceId={}", workspaceId);
            log.info("userId={}", userId);
            log.info("enabled={}", shipLogicProperties.isEnabled());
            log.info("mode={}", shipLogicProperties.getMode());
            log.info("baseUrl={}", shipLogicProperties.getBaseUrl());
            log.info("providerId={}", shipLogicProperties.getProviderId());
            log.info("apiKeyPresent={}", shipLogicProperties.getApiKey() != null
                && !shipLogicProperties.getApiKey().isBlank());
            log.info("configured={}", shipLogicProperties.isConfigured());
            log.info("parcelCount={}", request.getParcels() == null ? 0 : request.getParcels().size());

        if (!shipLogicProperties.isConfigured()) {
                log.error("########## SHIPLOGIC NOT CONFIGURED ##########");
                log.error("Set SHIPLOGIC_ENABLED=true and provide SHIPLOGIC_API_KEY before starting the app");
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Courier Guy sandbox is not configured"
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider_id", shipLogicProperties.getProviderId());
        payload.put("collection_address", toAddress(request.getCollectionAddress()));
        payload.put("delivery_address", toAddress(request.getDeliveryAddress()));
        payload.put("parcels", request.getParcels().stream()
                .map(this::toParcel)
                .toList());

        try {
            log.info("Calling ShipLogic rates endpoint: POST {}/v2/rates", shipLogicProperties.getBaseUrl());
            JsonNode response = restClientBuilder
                    .baseUrl(shipLogicProperties.getBaseUrl())
                    .build()
                    .post()
                    .uri("/v2/rates")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + shipLogicProperties.getApiKey()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            log.info("ShipLogic rates request completed successfully; responsePresent={}", response != null);
            log.info("########## SHIPLOGIC QUOTE END ##########");
            return response;
        } catch (Exception exception) {
            log.error("########## SHIPLOGIC REQUEST FAILED ##########", exception);
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Could not retrieve delivery rates from The Courier Guy"
            );
        }
    }

    private Map<String, Object> toAddress(DeliveryQuoteRequest.CollectionAddress address) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("company", address.getCompany());
        result.put("street_address", address.getStreetAddress());
        result.put("local_area", address.getLocalArea());
        result.put("city", address.getCity());
        result.put("zone", address.getZone());
        result.put("code", address.getPostalCode());
        result.put("country", address.getCountry());
        result.put("type", address.getType());
        return result;
    }

    private Map<String, Object> toAddress(DeliveryQuoteRequest.DeliveryAddress address) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("street_address", address.getStreetAddress());
        result.put("local_area", address.getLocalArea());
        result.put("city", address.getCity());
        result.put("zone", address.getZone());
        result.put("code", address.getPostalCode());
        result.put("country", address.getCountry());
        result.put("type", address.getType());
        return result;
    }

    private Map<String, Object> toParcel(DeliveryQuoteRequest.Parcel parcel) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submitted_length_cm", parcel.getLengthCm());
        result.put("submitted_width_cm", parcel.getWidthCm());
        result.put("submitted_height_cm", parcel.getHeightCm());
        result.put("submitted_weight_kg", parcel.getWeightKg());
        result.put("parcel_description", parcel.getDescription());
        return result;
    }
}