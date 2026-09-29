package sme.tech.innovators.sme.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sme.tech.innovators.sme.config.UberDirectProperties;
import sme.tech.innovators.sme.dto.request.UberDirectQuoteRequest;
import sme.tech.innovators.sme.dto.response.CheckoutDeliveryOptionsDto;
import sme.tech.innovators.sme.dto.response.UberDirectQuoteDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.OrderNotFoundException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.integration.uberdirect.UberDirectClient;
import sme.tech.innovators.sme.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Business-logic layer for Uber Direct delivery operations.
 *
 * <p>Two primary use-cases:
 * <ol>
 *   <li><b>Quote</b> — called from the customer checkout UI to display
 *       available delivery options and their price/ETA.</li>
 *   <li><b>Book</b> — called by the merchant after an order is paid to
 *       dispatch an Uber Direct courier and persist the {@link Delivery}
 *       record.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UberDirectService {

    private final UberDirectProperties uberDirectProperties;
    private final UberDirectClient uberDirectClient;
    private final WorkspaceDeliverySettingsRepository deliverySettingsRepository;
    private final WorkspaceRepository workspaceRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;

    // ── Public: quote at checkout ─────────────────────────────────────────

    /**
     * Returns a {@link CheckoutDeliveryOptionsDto} containing an Uber Direct
     * quote for the given store + dropoff address.
     *
     * <p>If Uber Direct is not enabled at either the platform or workspace
     * level, or the workspace has not configured a pickup address, an empty
     * options list is returned rather than an error — the checkout UI simply
     * does not show Uber Direct as an option.
     *
     * @param storeSlug the workspace's public slug (used to resolve context)
     * @param request   customer's dropoff address + order value
     */
    public CheckoutDeliveryOptionsDto getCheckoutOptions(String storeSlug,
                                                         UberDirectQuoteRequest request) {
        List<UberDirectQuoteDto> options = new ArrayList<>();

        // 1. Resolve workspace
        Workspace workspace = workspaceRepository.findByPublicSlugIgnoreCase(storeSlug)
                .orElseThrow(() -> new WorkspaceNotFoundException("Store not found: " + storeSlug));

        // 2. Check platform is configured
        if (!uberDirectProperties.isConfigured() && !uberDirectProperties.isMockMode()) {
            log.debug("UberDirect not configured at platform level — returning empty options");
            return CheckoutDeliveryOptionsDto.builder()
                    .storeSlug(storeSlug)
                    .options(options)
                    .build();
        }

        // 3. Check workspace has enabled Uber Direct and has a pickup address
        WorkspaceDeliverySettings settings = deliverySettingsRepository
                .findByWorkspaceId(workspace.getId())
                .orElse(null);

        if (settings == null || !settings.isUberDirectEnabled()) {
            log.debug("UberDirect not enabled for workspace {} — returning empty options",
                    workspace.getId());
            return CheckoutDeliveryOptionsDto.builder()
                    .storeSlug(storeSlug)
                    .options(options)
                    .build();
        }

        if (settings.getPickupAddressLine1() == null || settings.getPickupCity() == null) {
            log.warn("Workspace {} has UberDirect enabled but pickup address incomplete",
                    workspace.getId());
            return CheckoutDeliveryOptionsDto.builder()
                    .storeSlug(storeSlug)
                    .options(options)
                    .build();
        }

        // 4. Build pickup / dropoff maps and call the client
        try {
            Map<String, Object> pickup = buildPickupMap(settings);
            Map<String, Object> dropoff = buildDropoffMap(request);

            JsonNode quoteNode = uberDirectClient.createQuote(
                    pickup, dropoff, request.getManifestTotalValueCents());

            UberDirectQuoteDto quote = parseQuote(quoteNode);
            options.add(quote);
            log.info("UberDirect quote retrieved for store={} fee={} currency={}",
                    storeSlug, quote.getFee(), quote.getCurrency());

        } catch (ResponseStatusException rse) {
            // Log but don't blow up the checkout — treat unavailable as no option
            log.warn("UberDirect quote failed for store={}: {} {}",
                    storeSlug, rse.getStatusCode(), rse.getReason());
            options.add(unavailableOption(rse.getReason()));
        } catch (Exception e) {
            log.error("UberDirect quote unexpected error for store={}", storeSlug, e);
            options.add(unavailableOption("Uber Direct is temporarily unavailable"));
        }

        return CheckoutDeliveryOptionsDto.builder()
                .storeSlug(storeSlug)
                .options(options)
                .build();
    }

    // ── Merchant: book a delivery for a paid order ────────────────────────

    /**
     * Books an Uber Direct courier for a paid order and persists a
     * {@link Delivery} record linked to that order.
     *
     * @param workspaceId  the merchant's workspace
     * @param userId       authenticated merchant user id (ownership check)
     * @param orderId      the paid order to dispatch
     * @param quoteId      optional — the quote id from checkout; if null the
     *                     API will auto-select. Pass the value the customer
     *                     saw at checkout for price consistency.
     * @return the raw Uber Direct delivery response (also persisted)
     */
    @Transactional
    public JsonNode bookDelivery(UUID workspaceId, UUID userId,
                                  UUID orderId, String quoteId) {
        // 1. Ownership check
        Workspace workspace = workspaceRepository
                .findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or access denied"));

        // 2. Load order
        Order order = orderRepository.findByIdAndWorkspaceId(orderId, workspaceId)
                .orElseThrow(() -> new OrderNotFoundException(
                        "Order not found: " + orderId));

        // 3. Guard: don't create duplicate deliveries
        if (deliveryRepository.existsByOrderId(orderId)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "A delivery already exists for this order");
        }

        // 4. Load settings
        WorkspaceDeliverySettings settings = deliverySettingsRepository
                .findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(SERVICE_UNAVAILABLE,
                        "Uber Direct is not configured for this workspace"));

        if (!settings.isUberDirectEnabled()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Uber Direct has not been enabled for this workspace");
        }

        if (!uberDirectProperties.isConfigured() && !uberDirectProperties.isMockMode()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Uber Direct is not configured on this platform");
        }

        // 5. Build address maps
        Map<String, Object> pickup = buildPickupMap(settings);
        pickup.put("name", settings.getPickupContactName() != null
                ? settings.getPickupContactName()
                : workspace.getName());
        pickup.put("phone_number", settings.getPickupContactPhone());

        Map<String, Object> dropoff = buildDropoffFromOrder(order);

        // 6. Call Uber Direct
        String manifest = order.getItems().stream()
                .map(i -> i.getTitle() + " x" + i.getQuantity())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Order " + order.getOrderNumber());

        int manifestValueCents = order.getTotalAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        JsonNode response = uberDirectClient.createDelivery(
                quoteId, pickup, dropoff, manifest, manifestValueCents);

        // 7. Persist delivery
        persistDelivery(order, response);

        log.info("UberDirect delivery booked: orderId={} uberDeliveryId={}",
                orderId, response.path("id").asText());
        return response;
    }

    /**
     * Polls Uber Direct for the latest delivery status and updates the
     * persisted {@link Delivery} record.
     *
     * @param workspaceId the merchant's workspace
     * @param userId      authenticated merchant user id
     * @param orderId     order whose delivery to refresh
     * @return raw Uber Direct delivery status node
     */
    @Transactional
    public JsonNode refreshDeliveryStatus(UUID workspaceId, UUID userId, UUID orderId) {
        workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or access denied"));

        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                        "No delivery found for order: " + orderId));

        if (!"uber_direct".equals(delivery.getProvider())) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Delivery is not managed by Uber Direct");
        }

        JsonNode response = uberDirectClient.getDelivery(delivery.getExternalShipmentId());

        // Update status
        String rawStatus = response.path("status").asText("pending");
        DeliveryStatus newStatus = mapStatus(rawStatus);
        if (delivery.getStatus() != newStatus) {
            delivery.setStatus(newStatus);
            deliveryRepository.save(delivery);
            deliveryEventRepository.save(DeliveryEvent.builder()
                    .delivery(delivery)
                    .status(newStatus)
                    .source("uber_direct")
                    .note("Status refreshed from Uber Direct: " + rawStatus)
                    .build());
        }

        return response;
    }

    // ── Helpers: address builders ─────────────────────────────────────────

    private Map<String, Object> buildPickupMap(WorkspaceDeliverySettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("street_address", s.getPickupAddressLine1()
                + (s.getPickupAddressLine2() != null ? " " + s.getPickupAddressLine2() : ""));
        m.put("city", s.getPickupCity());
        m.put("state", s.getPickupProvince());
        m.put("zip_code", s.getPickupPostalCode());
        m.put("country", s.getPickupCountry() != null ? s.getPickupCountry() : "ZA");
        m.put("latitude", s.getPickupLatitude());
        m.put("longitude", s.getPickupLongitude());
        m.put("phone_number", s.getPickupContactPhone());
        return m;
    }

    private Map<String, Object> buildDropoffMap(UberDirectQuoteRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("street_address", r.getDropoffAddressLine1()
                + (r.getDropoffAddressLine2() != null ? " " + r.getDropoffAddressLine2() : ""));
        m.put("city", r.getDropoffCity());
        m.put("state", r.getDropoffProvince());
        m.put("zip_code", r.getDropoffPostalCode());
        m.put("country", r.getDropoffCountry());
        m.put("latitude", r.getDropoffLatitude());
        m.put("longitude", r.getDropoffLongitude());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildDropoffFromOrder(Order order) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> addr = order.getShippingAddress();
        if (addr != null) {
            String line1 = str(addr, "line1");
            String line2 = str(addr, "line2");
            m.put("street_address", line2 != null && !line2.isBlank()
                    ? line1 + " " + line2 : line1);
            m.put("city", str(addr, "city"));
            m.put("state", str(addr, "province"));
            m.put("zip_code", str(addr, "postalCode"));
            m.put("country", str(addr, "country"));
        }
        m.put("name", order.getCustomerName());
        m.put("phone_number", order.getCustomerPhone());
        return m;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    // ── Helpers: response parsing ─────────────────────────────────────────

    private UberDirectQuoteDto parseQuote(JsonNode node) {
        boolean available = !"no_couriers_available".equals(
                node.path("kind").asText(""))
                && !node.path("id").isMissingNode();

        if (!available) {
            return unavailableOption(node.path("message").asText(
                    "No Uber Direct couriers available in this area"));
        }

        // Uber Direct returns fee in cents
        JsonNode feeNode = node.path("fee");
        BigDecimal fee = feeNode.isNumber()
                ? feeNode.decimalValue().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String eta = buildEta(node);

        return UberDirectQuoteDto.builder()
                .quoteId(node.path("id").asText(null))
                .providerName("Uber Direct")
                .fee(fee)
                .currency("ZAR")
                .estimatedDeliveryTime(eta)
                .expiresAt(node.path("expires").asText(null))
                .available(true)
                .build();
    }

    private String buildEta(JsonNode node) {
        JsonNode pickupEta = node.path("pickup_eta");
        JsonNode dropoffEta = node.path("dropoff_eta");
        if (!pickupEta.isMissingNode() && !dropoffEta.isMissingNode()) {
            return pickupEta.asInt(0) + "–" + dropoffEta.asInt(0) + " min";
        }
        JsonNode duration = node.path("duration");
        if (!duration.isMissingNode()) {
            int mins = duration.asInt(0) / 60;
            return mins + " min";
        }
        return "Estimated delivery";
    }

    private UberDirectQuoteDto unavailableOption(String reason) {
        return UberDirectQuoteDto.builder()
                .providerName("Uber Direct")
                .available(false)
                .unavailableReason(reason)
                .fee(BigDecimal.ZERO)
                .currency("ZAR")
                .build();
    }

    // ── Helpers: persist ──────────────────────────────────────────────────

    @Transactional
    protected void persistDelivery(Order order, JsonNode response) {
        String rawStatus = response.path("status").asText("pending");
        DeliveryStatus status = mapStatus(rawStatus);

        String trackingUrl = response.path("tracking_url").asText(null);
        String courierName = response.path("courier").path("name").asText(null);

        Delivery delivery = Delivery.builder()
                .order(order)
                .provider("uber_direct")
                .carrierName(courierName != null ? courierName : "Uber Direct")
                .externalShipmentId(response.path("id").asText())
                .trackingUrl(trackingUrl)
                .status(status)
                .build();

        delivery = deliveryRepository.save(delivery);

        deliveryEventRepository.save(DeliveryEvent.builder()
                .delivery(delivery)
                .status(status)
                .source("uber_direct")
                .note("Uber Direct delivery created")
                .build());
    }

    private DeliveryStatus mapStatus(String uberStatus) {
        return switch (uberStatus.toLowerCase()) {
            case "pending"           -> DeliveryStatus.PENDING;
            case "pickup"            -> DeliveryStatus.ASSIGNED;
            case "pickup_complete"   -> DeliveryStatus.DISPATCHED;
            case "dropoff"           -> DeliveryStatus.IN_TRANSIT;
            case "delivered"         -> DeliveryStatus.DELIVERED;
            case "cancelled"         -> DeliveryStatus.CANCELLED;
            case "returned"          -> DeliveryStatus.FAILED;
            default                  -> DeliveryStatus.PENDING;
        };
    }
}
