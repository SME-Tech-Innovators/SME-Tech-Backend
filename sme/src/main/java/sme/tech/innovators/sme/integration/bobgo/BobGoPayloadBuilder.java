package sme.tech.innovators.sme.integration.bobgo;

import org.springframework.stereotype.Component;
import sme.tech.innovators.sme.dto.request.CheckoutRequest;
import sme.tech.innovators.sme.dto.common.ShippingAddressDto;
import sme.tech.innovators.sme.entity.Cart;
import sme.tech.innovators.sme.entity.CartItem;
import sme.tech.innovators.sme.entity.Order;
import sme.tech.innovators.sme.entity.OrderItem;

import java.math.BigDecimal;
import java.util.*;

@Component
public class BobGoPayloadBuilder {

    private static final double DEFAULT_WEIGHT_KG = 1.0;
    private static final int DEFAULT_LENGTH_CM = 20;
    private static final int DEFAULT_WIDTH_CM = 15;
    private static final int DEFAULT_HEIGHT_CM = 10;

    public Map<String, Object> buildRatesPayload(Map<String, Object> collectionAddress,
                                                  Map<String, Object> deliveryAddress,
                                                  List<Map<String, Object>> parcels,
                                                  BigDecimal declaredValue,
                                                  String collectionFullName,
                                                  String collectionEmail,
                                                  String collectionPhone,
                                                  String deliveryFullName,
                                                  String deliveryEmail,
                                                  String deliveryPhone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collection_address", toBobGoAddress(collectionAddress));
        body.put("delivery_address", toBobGoAddress(deliveryAddress));
        body.put("parcels", parcels);
        if (declaredValue != null) {
            body.put("declared_value", declaredValue);
        }
        putRatesContactFields(
                body,
                collectionFullName,
                collectionEmail,
                collectionPhone,
                deliveryFullName,
                deliveryEmail,
                deliveryPhone);
        return body;
    }

    public Map<String, Object> buildShipmentPayload(Map<String, Object> ratesPayload,
                                                     Map<String, Object> selectedRateMeta) {
        Map<String, Object> body = new LinkedHashMap<>(ratesPayload);
        stripLegacyContactFields(body);
        if (selectedRateMeta != null) {
            copyIfPresent(selectedRateMeta, body, "provider_slug");
            copyIfPresent(selectedRateMeta, body, "service_level_code");
            copyIfPresent(selectedRateMeta, body, "rate_request_id");
            copyIfPresent(selectedRateMeta, body, "courier_id");
            copyIfPresent(selectedRateMeta, body, "service_id");
        }
        return body;
    }

    public List<Map<String, Object>> parcelsFromCart(Cart cart) {
        List<Map<String, Object>> parcels = new ArrayList<>();
        int itemCount = cart.getItems() == null ? 0 : cart.getItems().size();
        int qty = cart.getItems() == null ? 1
                : cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();
        parcels.add(defaultParcel(Math.max(1, qty)));
        if (itemCount > 1) {
            // Bob Go may price multi-parcel differently; v1 sends one consolidated parcel.
        }
        return parcels;
    }

    public List<Map<String, Object>> parcelsFromOrder(Order order) {
        int qty = order.getItems() == null ? 1
                : order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return List.of(defaultParcel(Math.max(1, qty)));
    }

    public Map<String, Object> deliveryAddressFromCheckout(CheckoutRequest.ShippingAddress address) {
        return addressMap(
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getProvince(),
                address.getPostalCode(),
                address.getCountry());
    }

    public Map<String, Object> deliveryAddressFromQuote(ShippingAddressDto address) {
        return addressMap(
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getProvince(),
                address.getPostalCode(),
                address.getCountry());
    }

    /** Bob Go v2 POST /rates uses {@code collection_contact_full_name} (flat fields, not nested objects). */
    public void putRatesContactFields(Map<String, Object> body,
                                      String collectionFullName,
                                      String collectionEmail,
                                      String collectionPhone,
                                      String deliveryFullName,
                                      String deliveryEmail,
                                      String deliveryPhone) {
        stripLegacyContactFields(body);
        String collectionName = firstNonBlank(collectionFullName, "Store contact");
        body.put("collection_contact_full_name", collectionName);
        body.put("collection_contact_email", firstNonBlank(collectionEmail, "shipping@store.local"));
        body.put(
                "collection_contact_mobile_number",
                normalizePhone(firstNonBlank(collectionPhone, "+27800000000")));
        if (firstNonBlank(deliveryFullName, deliveryEmail, deliveryPhone) != null) {
            body.put(
                    "delivery_contact_full_name",
                    firstNonBlank(deliveryFullName, "Customer"));
            body.put(
                    "delivery_contact_email",
                    firstNonBlank(deliveryEmail, "customer@example.com"));
            body.put(
                    "delivery_contact_mobile_number",
                    normalizePhone(firstNonBlank(deliveryPhone, "+27800000000")));
        }
    }

    /** Bob Go v2 POST /shipments uses {@code collection_contact_name} (flat fields). */
    public Map<String, Object> applyShipmentContacts(Map<String, Object> body,
                                                       String collectionName,
                                                       String collectionEmail,
                                                       String collectionPhone,
                                                       String deliveryName,
                                                       String deliveryEmail,
                                                       String deliveryPhone) {
        Map<String, Object> merged = new LinkedHashMap<>(body);
        stripLegacyContactFields(merged);
        merged.put(
                "collection_contact_name",
                firstNonBlank(collectionName, "Store contact"));
        merged.put(
                "collection_contact_email",
                firstNonBlank(collectionEmail, "shipping@store.local"));
        merged.put(
                "collection_contact_mobile_number",
                normalizePhone(firstNonBlank(collectionPhone, "+27800000000")));
        merged.put(
                "delivery_contact_name",
                firstNonBlank(deliveryName, "Customer"));
        merged.put(
                "delivery_contact_email",
                firstNonBlank(deliveryEmail, "customer@example.com"));
        merged.put(
                "delivery_contact_mobile_number",
                normalizePhone(firstNonBlank(deliveryPhone, "+27800000000")));
        return merged;
    }

    private static void stripLegacyContactFields(Map<String, Object> body) {
        body.remove("collection_contact");
        body.remove("delivery_contact");
        body.remove("collection_contact_full_name");
        body.remove("collection_contact_name");
        body.remove("collection_contact_email");
        body.remove("collection_contact_mobile_number");
        body.remove("delivery_contact_full_name");
        body.remove("delivery_contact_name");
        body.remove("delivery_contact_email");
        body.remove("delivery_contact_mobile_number");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static Map<String, Object> defaultParcel(int quantity) {
        Map<String, Object> parcel = new LinkedHashMap<>();
        parcel.put("submitted_length_cm", DEFAULT_LENGTH_CM);
        parcel.put("submitted_width_cm", DEFAULT_WIDTH_CM);
        parcel.put("submitted_height_cm", DEFAULT_HEIGHT_CM);
        parcel.put("submitted_weight_kg", DEFAULT_WEIGHT_KG * Math.max(1, quantity));
        parcel.put("description", "Order items");
        return parcel;
    }

    static Map<String, Object> toBobGoAddress(Map<String, Object> source) {
        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("street_address", stringVal(source.get("line1")));
        String line2 = stringVal(source.get("line2"));
        if (line2 != null && !line2.isBlank()) {
            addr.put("local_area", line2);
        }
        addr.put("city", stringVal(source.get("city")));
        String province = stringVal(source.get("province"));
        if (province != null && !province.isBlank()) {
            addr.put("zone", province);
        }
        addr.put("country", normalizeCountry(stringVal(source.get("country"))));
        String postal = stringVal(source.get("postalCode"));
        if (postal != null && !postal.isBlank()) {
            addr.put("code", postal);
        }
        return addr;
    }

    private static Map<String, Object> addressMap(String line1,
                                                   String line2,
                                                   String city,
                                                   String province,
                                                   String postalCode,
                                                   String country) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line1", line1);
        map.put("line2", line2 != null ? line2 : "");
        map.put("city", city);
        map.put("province", province != null ? province : "");
        map.put("postalCode", postalCode != null ? postalCode : "");
        map.put("country", country);
        return map;
    }

    static String normalizePhone(String phone) {
        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        if (trimmed.startsWith("0")) {
            return "+27" + trimmed.substring(1);
        }
        return "+27" + trimmed;
    }

    static String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "ZA";
        }
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        Object val = from.get(key);
        if (val != null) {
            to.put(key, val);
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
