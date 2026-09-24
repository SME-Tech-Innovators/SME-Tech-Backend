package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.integration.bobgo.BobGoPayloadBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BobGoPayloadBuilderTest {

    private final BobGoPayloadBuilder builder = new BobGoPayloadBuilder();

    @Test
    void buildRatesPayloadUsesFlatContactFieldsRequiredByBobGoV2() {
        Map<String, Object> payload = builder.buildRatesPayload(
                Map.of("line1", "1 Warehouse Rd", "city", "Johannesburg", "country", "ZA"),
                Map.of("line1", "2 Customer Rd", "city", "Pretoria", "country", "ZA"),
                java.util.List.of(Map.of(
                        "submitted_length_cm", 20,
                        "submitted_width_cm", 15,
                        "submitted_height_cm", 10,
                        "submitted_weight_kg", 1,
                        "description", "Order items")),
                null,
                "My Store",
                "merchant@example.com",
                "+27821234567",
                null,
                null,
                null);

        assertThat(payload.get("collection_contact_full_name")).isEqualTo("My Store");
        assertThat(payload.get("collection_contact_email")).isEqualTo("merchant@example.com");
        assertThat(payload.get("collection_contact_mobile_number")).isEqualTo("+27821234567");
        assertThat(payload).doesNotContainKey("collection_contact");
    }

    @Test
    void applyShipmentContactsUsesFlatNameFieldsAndStripsLegacyNestedContacts() {
        Map<String, Object> cachedRates = new LinkedHashMap<>();
        cachedRates.put("collection_address", Map.of("street_address", "1 Warehouse Rd"));
        cachedRates.put("collection_contact", Map.of("name", "My", "surname", "Store"));
        cachedRates.put("collection_contact_full_name", "Stale Name");

        Map<String, Object> merged = builder.applyShipmentContacts(
                cachedRates,
                "My Store",
                "store@example.com",
                "+27821234567",
                "Ada Lovelace",
                "ada@example.com",
                "+27987654321");

        assertThat(merged.get("collection_contact_name")).isEqualTo("My Store");
        assertThat(merged.get("collection_contact_email")).isEqualTo("store@example.com");
        assertThat(merged.get("delivery_contact_name")).isEqualTo("Ada Lovelace");
        assertThat(merged).doesNotContainKey("collection_contact");
        assertThat(merged).doesNotContainKey("collection_contact_full_name");
    }
}
