package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import sme.tech.innovators.sme.dto.request.ReturnQuoteRequest;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.exception.*;
import sme.tech.innovators.sme.integration.bobgo.*;
import sme.tech.innovators.sme.integration.paystack.PaystackClient;
import sme.tech.innovators.sme.repository.*;
import sme.tech.innovators.sme.service.OrderReturnService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderReturnServiceTest {
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    OrderReturnRepository returns = mock(OrderReturnRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    WorkspaceShippingSettingsRepository settings = mock(WorkspaceShippingSettingsRepository.class);
    BobGoClient bobgo = mock(BobGoClient.class);
    PaystackClient paystack = mock(PaystackClient.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    OrderReturnService service = new OrderReturnService(workspaces, orders, returns, payments, settings,
            bobgo, new BobGoPayloadBuilder(), new BobGoRateMapper(), paystack, new TransactionTemplate(transactionManager));
    UUID workspaceId = UUID.randomUUID(), userId = UUID.randomUUID(), orderId = UUID.randomUUID();
    Order order;
    OrderReturn ret;
    Payment payment;

    @BeforeEach void setup() {
        Workspace workspace = Workspace.builder().id(workspaceId).build();
        order = Order.builder().id(orderId).workspace(workspace).orderNumber("ORD-1")
                .status(OrderStatus.FULFILLED).paymentStatus(PaymentStatus.PAID).shippingProvider("bobgo")
                .totalAmount(new BigDecimal("110.25")).subtotalAmount(new BigDecimal("100.00"))
                .customerName("Buyer").customerEmail("buyer@example.com").customerPhone("+27111111111")
                .shippingAddress(Map.of("line1", "Customer street", "city", "Cape Town", "country", "ZA")).build();
        ret = new OrderReturn(); ret.setOrderId(orderId); ret.setReason("Damaged");
        ret.setQuotedAt(LocalDateTime.now());
        ret.setRates(Map.of("rates", List.of(Map.of("provider_slug", "demo", "service_level_code", "ECO", "price", 50.0))));
        ret.setShipmentPayload(Map.of("collection_contact_name", "Buyer", "delivery_contact_name", "Seller"));
        when(workspaces.findByIdAndBusiness_Owner_Id(workspaceId, userId)).thenReturn(Optional.of(workspace));
        when(orders.lockForReturn(orderId, workspaceId)).thenReturn(Optional.of(order));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(returns.findById(orderId)).thenReturn(Optional.of(ret));
        payment = Payment.builder().id(UUID.randomUUID()).order(order).providerReference("paid-ref")
                .amount(order.getTotalAmount()).currency("ZAR").status(PaymentRecordStatus.PAID).build();
        when(payments.findByOrderIdAndStatus(orderId, PaymentRecordStatus.PAID)).thenReturn(List.of(payment));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
    }
    @Test void deniesOtherWorkspaceBeforeProviderCall() {
        when(workspaces.findByIdAndBusiness_Owner_Id(workspaceId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refund(workspaceId, userId, orderId)).isInstanceOf(WorkspaceNotFoundException.class);
        verifyNoInteractions(paystack, bobgo);
        verify(orders, never()).lockForReturn(any(), any());
    }
    @Test void cannotRefundBeforeReceipt() {
        assertThatThrownBy(() -> service.refund(workspaceId, userId, orderId)).hasMessageContaining("receipt");
        verifyNoInteractions(paystack);
    }
    @Test void refundUsesPaidTransactionAndCentsAndWaitsForProcessed() {
        ret.setReceivedAt(LocalDateTime.now());
        when(paystack.createRefund("paid-ref", 11025, "ZAR", "Damaged")).thenAnswer(invocation -> {
            assertThat(ret.getRefundStatus()).isEqualTo("SUBMITTING");
            verify(transactionManager).commit(any());
            return refundResponse("pending");
        });
        assertThat(service.refund(workspaceId, userId, orderId).refundStatus()).isEqualTo("PENDING");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        service.refund(workspaceId, userId, orderId);
        verify(paystack, times(1)).createRefund(anyString(), anyInt(), anyString(), anyString());
        when(paystack.fetchRefund("123")).thenReturn(refundResponse("processed"));
        service.refreshRefund(workspaceId, userId, orderId);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
    }
    @Test void ambiguousRefundCannotBeRepeated() {
        ret.setReceivedAt(LocalDateTime.now());
        when(paystack.createRefund(anyString(), anyInt(), anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        assertThat(service.refund(workspaceId, userId, orderId).refundStatus()).isEqualTo("UNKNOWN");
        service.refund(workspaceId, userId, orderId);
        verify(paystack, times(1)).createRefund(anyString(), anyInt(), anyString(), anyString());
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }
    @Test void multipleSuccessfulPaymentsRequireReconciliation() {
        ret.setReceivedAt(LocalDateTime.now());
        when(payments.findByOrderIdAndStatus(orderId, PaymentRecordStatus.PAID)).thenReturn(List.of(payment, payment));
        assertThatThrownBy(() -> service.refund(workspaceId, userId, orderId)).hasMessageContaining("Exactly one");
        verifyNoInteractions(paystack);
    }
    @Test void refundMismatchCannotMarkOrderRefunded() {
        ret.setReceivedAt(LocalDateTime.now());
        when(paystack.createRefund(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(Map.of("id", 123, "status", "processed", "amount", 1, "currency", "ZAR"));
        service.refund(workspaceId, userId, orderId);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ret.getRefundStatus()).isEqualTo("UNKNOWN");
    }
    @Test void quoteUsesReverseAddressesAndActualParcelMeasurements() {
        when(settings.findById(workspaceId)).thenReturn(Optional.of(WorkspaceShippingSettings.builder()
                .collectionAddress(Map.of("line1", "Merchant street", "city", "Pretoria", "country", "ZA")).build()));
        when(bobgo.postRates(any())).thenAnswer(call -> {
            Map<String,Object> payload = call.getArgument(0);
            assertThat(((Map<?,?>)payload.get("collection_address")).get("street_address")).isEqualTo("Customer street");
            assertThat(((Map<?,?>)payload.get("delivery_address")).get("street_address")).isEqualTo("Merchant street");
            assertThat(payload.get("collection_contact_full_name")).isEqualTo("Buyer");
            assertThat(payload.get("delivery_contact_full_name")).isEqualTo("Seller");
            assertThat(payload.get("parcels").toString()).contains("submitted_weight_kg=2");
            return ret.getRates();
        });
        var parcel = new ReturnQuoteRequest.Parcel("Box", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.valueOf(2));
        service.quote(workspaceId, userId, orderId, new ReturnQuoteRequest("Damaged", "Seller", "seller@example.com", "+27222222222", List.of(parcel)));
        assertThat(ret.getShipmentPayload()).containsEntry("collection_contact_name", "Buyer").containsEntry("delivery_contact_name", "Seller");
    }
    @Test void cannotBookArbitraryOrExpiredRate() {
        assertThatThrownBy(() -> service.createShipment(workspaceId, userId, orderId, "fake")).hasMessageContaining("Select an option");
        ret.setQuotedAt(LocalDateTime.now().minusHours(1));
        assertThatThrownBy(() -> service.createShipment(workspaceId, userId, orderId, "bobgo:demo:ECO:5000")).hasMessageContaining("expired");
        verifyNoInteractions(bobgo);
    }
    @Test void shipmentKeepsContactsAndIsSubmittedOnce() {
        when(bobgo.postShipments(any())).thenAnswer(call -> {
            assertThat(ret.getShipmentStatus()).isEqualTo("SUBMITTING");
            verify(transactionManager).commit(any());
            Map<String,Object> body = call.getArgument(0);
            assertThat(body).containsEntry("collection_contact_name", "Buyer")
                    .containsEntry("delivery_contact_name", "Seller").containsEntry("provider_slug", "demo");
            return Map.of("id", 42, "tracking_reference", "RET123");
        });
        assertThat(service.createShipment(workspaceId, userId, orderId, "bobgo:demo:ECO:5000").trackingReference()).isEqualTo("RET123");
        service.createShipment(workspaceId, userId, orderId, "bobgo:demo:ECO:5000");
        verify(bobgo, times(1)).postShipments(any());
        service.receive(workspaceId, userId, orderId);
        LocalDateTime received = ret.getReceivedAt();
        service.receive(workspaceId, userId, orderId);
        assertThat(ret.getReceivedAt()).isEqualTo(received);
        verifyNoInteractions(paystack);
    }
    @Test void ambiguousShipmentCannotBeRepeated() {
        when(bobgo.postShipments(any())).thenThrow(new RuntimeException("timeout"));
        assertThat(service.createShipment(workspaceId, userId, orderId, "bobgo:demo:ECO:5000").shipmentStatus()).isEqualTo("UNKNOWN");
        service.createShipment(workspaceId, userId, orderId, "bobgo:demo:ECO:5000");
        verify(bobgo, times(1)).postShipments(any());
        assertThatThrownBy(() -> service.receive(workspaceId, userId, orderId)).hasMessageContaining("must exist");
    }
    private Map<String,Object> refundResponse(String status) {
        return Map.of("id", 123, "amount", 11025, "currency", "ZAR", "status", status);
    }
}
