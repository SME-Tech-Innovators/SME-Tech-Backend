package sme.tech.innovators.sme.unit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.integration.bobgo.BobGoClient;
import sme.tech.innovators.sme.repository.*;
import sme.tech.innovators.sme.service.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BobGoCancellationServiceTest {
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    OrderShipmentRepository shipments = mock(OrderShipmentRepository.class);
    BobGoClient bobgo = mock(BobGoClient.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    BobGoShipmentStatusService statuses = new BobGoShipmentStatusService(orders, shipments);
    BobGoCancellationService service = new BobGoCancellationService(workspaces, orders, shipments, bobgo, statuses, new TransactionTemplate(tx));
    UUID w = UUID.randomUUID(), u = UUID.randomUUID(), o = UUID.randomUUID();
    Order order;
    OrderShipment shipment;
    @BeforeEach void setup() {
        order = Order.builder().id(o).status(OrderStatus.PROCESSING).paymentStatus(PaymentStatus.PAID).build();
        shipment = OrderShipment.builder().order(order).workspaceId(w).provider("bobgo").trackingReference("TRK").status(ShipmentStatus.CREATED).build();
        when(workspaces.findByIdAndBusiness_Owner_Id(w, u)).thenReturn(Optional.of(Workspace.builder().id(w).build()));
        when(orders.lockForReturn(o, w)).thenReturn(Optional.of(order));
        when(shipments.findByOrderId(o)).thenReturn(Optional.of(shipment));
    }
    @Test void unauthorizedRequestsNeverReachProvider() {
        when(workspaces.findByIdAndBusiness_Owner_Id(w, u)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(w, u, o)).hasMessageContaining("access denied");
        verifyNoInteractions(bobgo);
    }
    @Test void durableClaimPrecedesCallAndCancelledIsIdempotent() {
        when(bobgo.cancelShipment("TRK")).thenAnswer(inv -> {
            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCEL_REQUESTED);
            verify(tx).commit(any());
            return Map.of("status", "cancelled");
        });
        when(bobgo.getTracking("TRK")).thenReturn(Map.of("status", "cancelled"));
        service.cancel(w, u, o); service.cancel(w, u, o);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(bobgo, times(1)).cancelShipment("TRK");
    }
    @Test void timeoutBlocksFurtherProviderRequests() {
        when(bobgo.cancelShipment("TRK")).thenThrow(new RuntimeException("timeout"));
        service.cancel(w, u, o);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLATION_UNKNOWN);
        service.cancel(w, u, o);
        verify(bobgo, times(1)).cancelShipment("TRK");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }
    @Test void acceptedButUnconfirmedIsNotCancelled() {
        when(bobgo.cancelShipment("TRK")).thenReturn(Map.of("success", true));
        when(bobgo.getTracking("TRK")).thenReturn(Map.of("status", "submitted"));
        service.cancel(w, u, o);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCEL_REQUESTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        service.cancel(w, u, o);
        verify(bobgo, times(1)).cancelShipment("TRK");
    }
    @Test void cannotCancelAfterCollection() {
        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        assertThatThrownBy(() -> service.cancel(w, u, o)).hasMessageContaining("before collection");
        verifyNoInteractions(bobgo);
    }
}
