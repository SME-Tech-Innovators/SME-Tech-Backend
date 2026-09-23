package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.integration.bobgo.BobGoRateMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BobGoRateMapperTest {

    private final BobGoRateMapper mapper = new BobGoRateMapper();

    @Test
    void parsesRatesArrayWithDecimalPrice() {
        Map<String, Object> response = Map.of(
                "rate_request_id", "req-1",
                "rates", List.of(
                        Map.of(
                                "service_name", "Standard",
                                "provider_slug", "courier-x",
                                "service_level_code", "STD",
                                "price", 89.00,
                                "estimated_delivery_days", 3
                        )
                )
        );

        var parsed = mapper.parseRatesResponse(response, "ZAR");
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).amountMinor()).isEqualTo(8900);
        assertThat(parsed.get(0).optionId()).startsWith("bobgo:courier-x:STD:");
    }

    @Test
    void parsesNestedServiceLevelObjectLikeSandboxEco() {
        Map<String, Object> response = Map.of(
                "id", 521006,
                "provider_rate_requests", List.of(
                        Map.of(
                                "provider_slug", "sandbox",
                                "status", "success",
                                "responses", List.of(
                                        Map.of(
                                                "service_level_code", "ECO",
                                                "service_level", Map.of(
                                                        "code", "ECO",
                                                        "name", "Economy",
                                                        "description", "72–96 hours",
                                                        "delivery_type", "door"
                                                ),
                                                "rate_amount", 114.95,
                                                "status", "success"
                                        )
                                )
                        )
                )
        );

        var parsed = mapper.parseRatesResponse(response, "ZAR");
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).label()).isEqualTo("Economy (ECO)");
        assertThat(parsed.get(0).amountMinor()).isEqualTo(11495);
    }

    @Test
    void parsesProviderRateRequestsFromBobGoV2RatesResponse() {
        Map<String, Object> response = Map.of(
                "id", 521006,
                "provider_rate_requests", List.of(
                        Map.of(
                                "provider_slug", "sandbox",
                                "status", "success",
                                "responses", List.of(
                                        Map.of(
                                                "service_level_code", "LOF",
                                                "service_level", "Local Overnight",
                                                "rate_amount", 89.00,
                                                "status", "success"
                                        )
                                )
                        )
                )
        );

        var parsed = mapper.parseRatesResponse(response, "ZAR");
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).amountMinor()).isEqualTo(8900);
        assertThat(parsed.get(0).label()).isEqualTo("Local Overnight");
        assertThat(parsed.get(0).optionId()).contains("sandbox:LOF:");
    }
}
