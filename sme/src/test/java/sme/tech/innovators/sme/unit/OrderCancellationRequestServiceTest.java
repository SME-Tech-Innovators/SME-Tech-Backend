package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.repository.*;
import sme.tech.innovators.sme.service.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCancellationRequestServiceTest {
    CustomerOrderAccessService access = mock(CustomerOrderAccessService.class);
    OrderRepository orders = mock(OrderRepository.class);
    OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    BobGoCancellationService provider = mock(BobGoCancellationService.class);
    InventoryService inventory = mock(InventoryService.class);
    OrderCancellationRequestService service = new OrderCancellationRequestService(access, orders, shipments,
            workspaces, provider, inventory, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    UUID w = UUID.randomUUID(), u = UUID.randomUUID(), id = UUID.randomUUID();
    Order order;
    OrderShipment shipment;
    @BeforeEach void setup() {
        var workspace = Workspace.builder().id(w).build();
        order = Order.builder().id(id).workspace(workspace).status(OrderStatus.PROCESSING)
                .paymentStatus(PaymentStatus.PAID).shippingProvider("bobgo").build();
        shipment = OrderShipment.builder().order(order).status(ShipmentStatus.CREATED).trackingReference("TRK").build();
        when(access.authorize("shop", id, "token")).thenReturn(order);
        when(orders.lockForReturn(id, w)).thenReturn(Optional.of(order));
        when(orders.findByIdAndWorkspaceId(id, w)).thenReturn(Optional.of(order));
        when(shipments.findByOrderId(id)).thenReturn(Optional.of(shipment));
        when(workspaces.findByIdAndBusiness_Owner_Id(w, u)).thenReturn(Optional.of(workspace));
    }
    void request() { service.request("shop", id, "token", " Changed my mind "); }
    @Test void requestingIsIdempotentAndDoesNotCancelOrRefund() {
        request(); request();
        assertThat(order.getCancellationRequestStatus()).isEqualTo("REQUESTED");
        assertThat(order.getCancellationRequestReason()).isEqualTo("Changed my mind");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(service.customerStatus("shop", id, "token").canRequest()).isFalse();
        verify(orders, times(1)).save(order);
        verifyNoInteractions(provider, inventory);
    }
    @Test void invalidAccessCannotCreateRequest() {
        when(access.authorize("shop", id, "token")).thenThrow(new RuntimeException("invalid"));
        assertThatThrownBy(this::request).hasMessage("invalid");
        verify(orders, never()).lockForReturn(any(), any());
        verifyNoInteractions(provider, inventory);
    }
    @Test void collectedAndDeliveredOrdersCannotRequestCancellation() {
        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        assertThatThrownBy(this::request).hasMessageContaining("delivery stage");
        shipment.setStatus(ShipmentStatus.CREATED);
        order.setStatus(OrderStatus.FULFILLED);
        assertThatThrownBy(this::request).hasMessageContaining("before dispatch");
        assertThat(order.getCancellationRequestStatus()).isNull();
    }
    @Test void unpaidOrdersAndMissingCarrierShipmentAreIneligible() {
        order.setPaymentStatus(PaymentStatus.UNPAID);
        assertThatThrownBy(this::request).hasMessageContaining("payment");
        order.setPaymentStatus(PaymentStatus.PAID);
        when(shipments.findByOrderId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(this::request).hasMessageContaining("delivery stage");
    }
    @Test void merchantApprovalCallsProviderOnceWithoutInventingCancellation() {
        request();
        service.review(w, u, id, "approve", "Accepted");
        service.review(w, u, id, "approve", "Accepted");
        assertThat(order.getCancellationRequestStatus()).isEqualTo("APPROVED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(provider, times(1)).cancel(w, u, id);
        verifyNoInteractions(inventory);
    }
    @Test void approvalRechecksShipmentProgress() {
        request(); shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        assertThatThrownBy(() -> service.review(w, u, id, "approve", null)).hasMessageContaining("delivery stage");
        verifyNoInteractions(provider);
    }
    @Test void rejectionLeavesOrderAndPaymentUnchanged() {
        request(); service.review(w, u, id, "reject", "Already packed");
        assertThat(order.getCancellationRequestStatus()).isEqualTo("REJECTED");
        assertThat(order.getCancellationReviewNote()).isEqualTo("Already packed");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verifyNoInteractions(provider, inventory);
    }
    @Test void otherMerchantCannotReview() {
        request(); when(workspaces.findByIdAndBusiness_Owner_Id(w, u)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.review(w, u, id, "approve", null)).hasMessageContaining("access denied");
        verifyNoInteractions(provider, inventory);
    }
    @Test void nonCarrierApprovalRestocksOnceWithoutRefunding() {
        order.setShippingProvider(null); when(shipments.findByOrderId(id)).thenReturn(Optional.empty());
        request(); service.review(w, u, id, "approve", null); service.review(w, u, id, "approve", null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(inventory, times(1)).restockForCancelledOrder(order);
        verifyNoInteractions(provider);
    }
    @Test void failedProviderClaimReturnsRequestToReview() {
        request(); doThrow(new RuntimeException("claim failed")).when(provider).cancel(w, u, id);
        assertThatThrownBy(() -> service.review(w, u, id, "approve", null)).hasMessage("claim failed");
        assertThat(order.getCancellationRequestStatus()).isEqualTo("REQUESTED");
        assertThat(order.getCancellationReviewedAt()).isNull();
    }
    @Test void reviewWithoutRequestAndConflictingDecisionsAreRejected() {
        assertThatThrownBy(() -> service.review(w, u, id, "approve", null)).hasMessageContaining("No pending");
        request(); service.review(w, u, id, "reject", null);
        assertThatThrownBy(() -> service.review(w, u, id, "approve", null)).hasMessageContaining("No pending");
        verifyNoInteractions(provider);
    }
}
