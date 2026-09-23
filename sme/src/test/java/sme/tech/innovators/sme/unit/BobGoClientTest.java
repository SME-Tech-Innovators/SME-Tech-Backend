package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import sme.tech.innovators.sme.config.BobGoConfig;
import sme.tech.innovators.sme.integration.bobgo.BobGoClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BobGoClientTest {

    private MockRestServiceServer server;
    private BobGoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.sandbox.bobgo.co.za/v2");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BobGoConfig config = org.mockito.Mockito.mock(BobGoConfig.class);
        org.mockito.Mockito.when(config.getBearerToken()).thenReturn("test-token");
        client = new BobGoClient(restClient, config);
    }

    @Test
    void postRatesSendsBearerAuth() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/rates")))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"rates\":[]}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.postRates(Map.of("collection_address", Map.of()));
        assertThat(result).containsKey("rates");
        server.verify();
    }

    @Test
    void cancellationUsesProviderEndpointAndTrackingReference() {
        server.expect(requestTo("https://api.sandbox.bobgo.co.za/v2/shipments/cancel"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json("{\"tracking_reference\":\"TRK1\"}"))
                .andRespond(withSuccess("{\"status\":\"cancelled\"}", MediaType.APPLICATION_JSON));
        assertThat(client.cancelShipment("TRK1")).containsEntry("status", "cancelled");
        server.verify();
    }

    @Test
    void trackingSelectsRequestedShipmentFromArray() {
        trackingResponse("""
                [{"shipment_tracking_reference":"OTHER","status":"delivered"},
                 {"shipment_tracking_reference":"TRK1","status":"pending-collection"}]
                """);
        var result = client.getTracking("TRK1");
        assertThat(result).containsEntry("status", "pending-collection");
        assertThat(sme.tech.innovators.sme.integration.bobgo.BobGoShipmentStatuses.parse(result))
                .isEqualTo(sme.tech.innovators.sme.entity.ShipmentStatus.CREATED);
        server.verify();
    }

    @Test
    void trackingAcceptsObjectResponse() {
        trackingResponse("{\"status\":\"delivered\"}");
        assertThat(client.getTracking("TRK1")).containsEntry("status", "delivered");
        server.verify();
    }

    @Test
    void emptyTrackingDoesNotInventAStatus() {
        trackingResponse("[]");
        assertThat(client.getTracking("TRK1")).isEmpty();
        server.verify();
    }

    @Test
    void trackingRejectsAnUnrelatedShipment() {
        trackingResponse("[{\"shipment_tracking_reference\":\"OTHER\",\"status\":\"delivered\"}]");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getTracking("TRK1"))
                .isInstanceOf(sme.tech.innovators.sme.exception.ShipmentCreateFailedException.class);
        server.verify();
    }

    @Test
    void malformedTrackingReturnsProviderError() {
        trackingResponse("invalid json");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getTracking("TRK1"))
                .isInstanceOf(sme.tech.innovators.sme.exception.ShipmentCreateFailedException.class);
        server.verify();
    }

    private void trackingResponse(String body) {
        server.expect(requestTo("https://api.sandbox.bobgo.co.za/v2/tracking?tracking_reference=TRK1"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
