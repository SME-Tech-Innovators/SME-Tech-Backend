package sme.tech.innovators.sme.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import sme.tech.innovators.sme.config.ShipLogicProperties;
import sme.tech.innovators.sme.dto.request.CreateShipLogicShipmentRequest;
import sme.tech.innovators.sme.entity.Delivery;
import sme.tech.innovators.sme.entity.DeliveryEvent;
import sme.tech.innovators.sme.entity.DeliveryStatus;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.exception.InvalidOrderStatusTransitionException;
import sme.tech.innovators.sme.exception.OrderNotFoundException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.DeliveryEventRepository;
import sme.tech.innovators.sme.repository.DeliveryRepository;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipLogicShipmentService {

    private final ShipLogicProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final WorkspaceRepository workspaceRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;

    @Transactional
    public JsonNode create(UUID workspaceId, UUID userId, UUID orderId,
                           CreateShipLogicShipmentRequest request) {
        loadOwnedWorkspace(workspaceId, userId);
        Order order = orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        if (deliveryRepository.existsByOrderId(orderId)) {
            throw new InvalidOrderStatusTransitionException("Delivery already exists for this order");
        }
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Courier Guy sandbox is not configured");
        }
        if ((request.getServiceLevelId() == null)
                && (request.getServiceLevelCode() == null || request.getServiceLevelCode().isBlank())) {
            throw new IllegalArgumentException("serviceLevelId or serviceLevelCode is required");
        }

        Map<String, Object> payload = toPayload(request);
        try {
            JsonNode response = restClientBuilder
                    .baseUrl(properties.getBaseUrl())
                    .build()
                    .post()
                    .uri("/shipments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.path("id").isMissingNode()) {
                throw new ResponseStatusException(BAD_GATEWAY,
                        "ShipLogic returned an invalid shipment response");
            }
            persistShipment(order, request, response);
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("ShipLogic shipment creation failed for order={}", orderId, exception);
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Could not create shipment with The Courier Guy");
        }
    }

    private Map<String, Object> toPayload(CreateShipLogicShipmentRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collection_address", address(request.getCollectionAddress()));
        payload.put("collection_contact", contact(request.getCollectionContact()));
        payload.put("delivery_address", address(request.getDeliveryAddress()));
        payload.put("delivery_contact", contact(request.getDeliveryContact()));
        payload.put("parcels", request.getParcels().stream().map(this::parcel).toList());
        if (request.getServiceLevelId() != null) payload.put("service_level_id", request.getServiceLevelId());
        if (request.getServiceLevelCode() != null && !request.getServiceLevelCode().isBlank()) {
            payload.put("service_level_code", request.getServiceLevelCode());
        }
        payload.put("customer_reference", request.getCustomerReference());
        payload.put("customer_reference_name", request.getCustomerReferenceName());
        payload.put("declared_value", request.getDeclaredValue());
        payload.put("mute_notifications", request.isMuteNotifications());
        payload.put("special_instructions_collection", request.getSpecialInstructionsCollection());
        payload.put("special_instructions_delivery", request.getSpecialInstructionsDelivery());
        return payload;
    }

    private Map<String, Object> address(CreateShipLogicShipmentRequest.Address value) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("type", value.getType());
        address.put("company", value.getCompany());
        address.put("street_address", value.getStreetAddress());
        address.put("local_area", value.getLocalArea());
        address.put("city", value.getCity());
        address.put("zone", value.getZone());
        address.put("country", value.getCountry());
        address.put("code", value.getPostalCode());
        address.put("lat", value.getLat());
        address.put("lng", value.getLng());
        return address;
    }

    private Map<String, Object> contact(CreateShipLogicShipmentRequest.Contact value) {
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("name", value.getName());
        contact.put("email", value.getEmail());
        contact.put("mobile_number", value.getMobileNumber());
        return contact;
    }

    private Map<String, Object> parcel(CreateShipLogicShipmentRequest.Parcel value) {
        Map<String, Object> parcel = new LinkedHashMap<>();
        parcel.put("parcel_description", value.getParcelDescription());
        parcel.put("submitted_length_cm", value.getLengthCm());
        parcel.put("submitted_width_cm", value.getWidthCm());
        parcel.put("submitted_height_cm", value.getHeightCm());
        parcel.put("submitted_weight_kg", value.getWeightKg());
        return parcel;
    }

    private void persistShipment(Order order, CreateShipLogicShipmentRequest request, JsonNode response) {
        JsonNode firstParcel = response.path("parcels").path(0);
        DeliveryStatus status = mapStatus(response.path("status").asText("submitted"));
        Delivery delivery = Delivery.builder()
                .order(order)
                .provider("shiplogic")
                .externalShipmentId(response.path("id").asText())
                .trackingNumber(response.path("short_tracking_reference").asText(null))
                .parcelTrackingNumber(firstParcel.path("tracking_reference").asText(null))
                .carrierName("The Courier Guy")
                .serviceLevelCode(response.path("service_level_code").asText(request.getServiceLevelCode()))
                .serviceLevelName(response.path("service_level_name").asText(null))
                .quotedAmount(decimal(response.path("rate")))
                .status(status)
                .estimatedDeliveryAt(null)
                .build();
        delivery = deliveryRepository.save(delivery);
        deliveryEventRepository.save(DeliveryEvent.builder()
                .delivery(delivery)
                .status(status)
                .source("courier")
                .note("Shipment created in ShipLogic")
                .build());
        log.info("ShipLogic shipment persisted: orderId={}, externalShipmentId={}, trackingNumber={}",
                order.getId(), delivery.getExternalShipmentId(), delivery.getTrackingNumber());
    }

    private DeliveryStatus mapStatus(String status) {
        return switch (status.toLowerCase()) {
            case "collection-assigned", "submitted" -> DeliveryStatus.ASSIGNED;
            case "collected" -> DeliveryStatus.DISPATCHED;
            case "in-transit", "at-hub", "at-destination-hub" -> DeliveryStatus.IN_TRANSIT;
            case "out-for-delivery" -> DeliveryStatus.OUT_FOR_DELIVERY;
            case "delivered" -> DeliveryStatus.DELIVERED;
            case "cancelled" -> DeliveryStatus.CANCELLED;
            case "delivery-failed-attempt", "delivery-exception", "collection-failed-attempt" -> DeliveryStatus.FAILED;
            default -> DeliveryStatus.PENDING;
        };
    }

    private BigDecimal decimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : null;
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }
}
