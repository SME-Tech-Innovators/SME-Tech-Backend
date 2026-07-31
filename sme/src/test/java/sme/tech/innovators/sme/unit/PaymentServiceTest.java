package sme.tech.innovators.sme.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import sme.tech.innovators.sme.dto.request.InitializePaymentRequest;
import sme.tech.innovators.sme.dto.response.PaymentInitDto;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.PaymentNotConfiguredException;
import sme.tech.innovators.sme.exception.PaymentWebhookInvalidException;
import sme.tech.innovators.sme.integration.paystack.PaystackClient;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.PaymentRepository;
import sme.tech.innovators.sme.service.CheckoutService;
import sme.tech.innovators.sme.service.OrderConfirmationMailer;
import sme.tech.innovators.sme.service.PaymentService;
import sme.tech.innovators.sme.service.PublicStoreResolver;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PublicStoreResolver publicStoreResolver;
    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaystackClient paystackClient;
    @Mock OrderConfirmationMailer orderConfirmationMailer;
    @Mock CheckoutService checkoutService;

    private PaymentService paymentService;
    private Workspace workspace;
    private Order order;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                publicStoreResolver,
                orderRepository,
                paymentRepository,
                paystackClient,
                new ObjectMapper(),
                orderConfirmationMailer,
                checkoutService);
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "https://sme-operations.netlify.app");
        workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Bridge Labs")
                .publicSlug("bridge-labs")
                .status(WorkspaceStatus.LIVE)
                .paystackSubaccountStatus(PaystackSubaccountStatus.ACTIVE)
                .paystackSubaccountCode("ACCT_abc")
                .build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .orderNumber("ORD-1")
                .customerName("Ada")
                .customerEmail("ada@example.com")
                .customerPhone("+27000000000")
                .subtotalAmount(10000)
                .shippingAmount(0)
                .totalAmount(10000)
                .currency("ZAR")
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();
        lenient().when(publicStoreResolver.requireLiveWorkspace("bridge-labs")).thenReturn(workspace);
        lenient().when(paystackClient.getPublicKey()).thenReturn("pk_test_dummy");
        lenient().when(paystackClient.getWebhookSecret()).thenReturn("whsec_test");
    }

    @Test
    void payRefusedWhenSubaccountNotActive() {
        workspace.setPaystackSubaccountStatus(PaystackSubaccountStatus.NOT_CONNECTED);
        assertThatThrownBy(() -> paymentService.initializePayment(
                "bridge-labs", order.getId().toString(), new InitializePaymentRequest()))
                .isInstanceOf(PaymentNotConfiguredException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void payUsesDefaultCallbackWhenOmitted() {
        when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspace.getId()))
                .thenReturn(Optional.of(order));
        when(paystackClient.initializeTransaction(
                eq("ada@example.com"), eq(10000), eq("ZAR"), anyString(),
                eq("https://sme-operations.netlify.app/s/bridge-labs/order/" + order.getId()),
                eq("ACCT_abc"), anyMap()))
                .thenReturn(Map.of(
                        "authorization_url", "https://checkout.paystack.com/x",
                        "access_code", "ACCESS_X"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.initializePayment("bridge-labs", order.getId().toString(), new InitializePaymentRequest());

        verify(paystackClient).initializeTransaction(
                eq("ada@example.com"), eq(10000), eq("ZAR"), anyString(),
                eq("https://sme-operations.netlify.app/s/bridge-labs/order/" + order.getId()),
                eq("ACCT_abc"), anyMap());
    }

    @Test
    void payForwardsExplicitCallbackUrl() {
        when(orderRepository.findByIdAndWorkspaceId(order.getId(), workspace.getId()))
                .thenReturn(Optional.of(order));
        String callback = "https://sme-operations.netlify.app/s/bridge-labs/order/" + order.getId();
        InitializePaymentRequest request = new InitializePaymentRequest();
        request.setCallbackUrl(callback);
        when(paystackClient.initializeTransaction(
                anyString(), anyInt(), anyString(), anyString(), eq(callback), anyString(), anyMap()))
                .thenReturn(Map.of(
                        "authorization_url", "https://checkout.paystack.com/x",
                        "access_code", "ACCESS_X"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentInitDto dto = paymentService.initializePayment(
                "bridge-labs", order.getId().toString(), request);

        assertThat(dto.getAccessCode()).isEqualTo("ACCESS_X");
        verify(paystackClient).initializeTransaction(
                anyString(), anyInt(), anyString(), anyString(), eq(callback), anyString(), anyMap());
    }

    @Test
    void webhookInvalidSignatureRejected() {
        assertThatThrownBy(() -> paymentService.handleWebhook("bad-sig", "{\"event\":\"charge.success\"}"))
                .isInstanceOf(PaymentWebhookInvalidException.class);
    }

    @Test
    void webhookChargeSuccessMarksPaymentAndOrderPaidAndSchedulesEmail() throws Exception {
        String body = """
                {"event":"charge.success","data":{"reference":"ord_ref_1","amount":10000}}
                """;
        String signature = hmac(body, "whsec_test");

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .workspace(workspace)
                .providerReference("ord_ref_1")
                .amount(10000)
                .currency("ZAR")
                .status(PaymentRecordStatus.INITIALIZED)
                .build();
        when(paymentRepository.findByProviderReference("ord_ref_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleWebhook(signature, body);

        assertThat(payment.getStatus()).isEqualTo(PaymentRecordStatus.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderConfirmationMailer).scheduleAfterPayment(order.getId());
    }

    @Test
    void duplicateWebhookDoesNotDoubleApplyButMayRetryEmail() throws Exception {
        String body = """
                {"event":"charge.success","data":{"reference":"ord_ref_1","amount":10000}}
                """;
        String signature = hmac(body, "whsec_test");

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PAID);
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .workspace(workspace)
                .providerReference("ord_ref_1")
                .amount(10000)
                .currency("ZAR")
                .status(PaymentRecordStatus.PAID)
                .build();
        when(paymentRepository.findByProviderReference("ord_ref_1")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(signature, body);

        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(orderConfirmationMailer).scheduleAfterPayment(order.getId());
    }

    private static String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
