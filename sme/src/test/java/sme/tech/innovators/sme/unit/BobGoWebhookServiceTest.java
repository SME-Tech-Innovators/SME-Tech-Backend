package sme.tech.innovators.sme.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.config.BobGoConfig;
import sme.tech.innovators.sme.entity.OrderShipment;
import sme.tech.innovators.sme.entity.ShipmentStatus;
import sme.tech.innovators.sme.exception.BobGoWebhookInvalidException;
import sme.tech.innovators.sme.repository.OrderShipmentRepository;
import sme.tech.innovators.sme.service.BobGoWebhookService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BobGoWebhookServiceTest {

    private static final String SECRET = "test-bobgo-webhook-secret";

    @Mock BobGoConfig bobGoConfig;
    @Mock OrderShipmentRepository orderShipmentRepository;

    @Mock sme.tech.innovators.sme.service.BobGoShipmentStatusService shipmentStatusService;
    private BobGoWebhookService service;

    @BeforeEach
    void setUp() {
        when(bobGoConfig.getWebhookSecret()).thenReturn(SECRET);
        service = new BobGoWebhookService(bobGoConfig, orderShipmentRepository, new ObjectMapper(), shipmentStatusService);
    }

    @Test
    void rejectsMissingSignature() {
        assertThatThrownBy(() -> service.handleWebhook(null, "{}"))
                .isInstanceOf(BobGoWebhookInvalidException.class)
                .hasMessageContaining("Missing bobgo-webhook-signature");
    }

    @Test
    void acceptsHmacSha256HexSignature() throws Exception {
        String body = "{\"data\":{\"shipment_id\":\"bg_1\",\"tracking_reference\":\"TRK1\",\"status\":\"created\"}}";
        String sig = hmacHex(body, SECRET);

        OrderShipment shipment = OrderShipment.builder()
                .id(UUID.randomUUID())
                .workspaceId(UUID.randomUUID())
                .order(sme.tech.innovators.sme.entity.Order.builder().id(UUID.randomUUID()).build())
                .status(ShipmentStatus.PENDING)
                .build();
        when(orderShipmentRepository.findByBobgoShipmentId("bg_1")).thenReturn(Optional.of(shipment));

        service.handleWebhook(sig, body);

        verify(shipmentStatusService).apply(org.mockito.ArgumentMatchers.eq(shipment.getWorkspaceId()),
                org.mockito.ArgumentMatchers.eq(shipment.getOrder().getId()), any());
    }

    private static String hmacHex(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
