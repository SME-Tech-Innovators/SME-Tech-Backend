package sme.tech.innovators.sme.unit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import sme.tech.innovators.sme.config.PaystackConfig;
import sme.tech.innovators.sme.integration.paystack.PaystackClient;
import sme.tech.innovators.sme.exception.PaymentInitializationFailedException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PaystackRefundClientTest {
    @Test void createsAndFetchesRefundUsingProviderContract() {
        var builder = RestClient.builder().baseUrl("https://api.paystack.co");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PaystackClient(builder.build(), mock(PaystackConfig.class), new ObjectMapper());
        server.expect(requestTo("https://api.paystack.co/refund")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"transaction\":\"paid-ref\",\"amount\":11025,\"currency\":\"ZAR\",\"merchant_note\":\"Damaged\"}"))
                .andRespond(withSuccess("{\"status\":true,\"data\":{\"id\":123,\"status\":\"pending\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.paystack.co/refund/123")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":true,\"data\":{\"id\":123,\"status\":\"processed\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.createRefund("paid-ref", 11025, "ZAR", "Damaged")).containsEntry("status", "pending");
        assertThat(client.fetchRefund("123")).containsEntry("status", "processed");
        server.verify();
    }
    @Test void unsuccessfulResponseIsNotAcceptedAsRefund() {
        var builder = RestClient.builder().baseUrl("https://api.paystack.co");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PaystackClient(builder.build(), mock(PaystackConfig.class), new ObjectMapper());
        server.expect(requestTo("https://api.paystack.co/refund"))
                .andRespond(withSuccess("{\"status\":false,\"message\":\"Already refunded\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.createRefund("paid-ref", 11025, "ZAR", "Damaged"))
                .isInstanceOf(PaymentInitializationFailedException.class);
        server.verify();
    }
}
