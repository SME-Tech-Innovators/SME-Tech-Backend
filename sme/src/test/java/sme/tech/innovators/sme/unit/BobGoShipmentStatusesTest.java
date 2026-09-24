package sme.tech.innovators.sme.unit;
import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.entity.*;
import sme.tech.innovators.sme.integration.bobgo.BobGoShipmentStatuses;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class BobGoShipmentStatusesTest {
    Order order = Order.builder().status(OrderStatus.PAID).paymentStatus(PaymentStatus.PAID).build();
    OrderShipment shipment = OrderShipment.builder().order(order).provider("bobgo").status(ShipmentStatus.CREATED).build();
    @Test void deliveryAttemptsAreNotDelivered() {
        for (String status : new String[]{"out-for-delivery", "delivery-failed", "delivery-attempted"})
            assertThat(BobGoShipmentStatuses.parse(Map.of("status", status))).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(BobGoShipmentStatuses.parse(Map.of("status", "unknown-new-status"))).isNull();
        assertThat(BobGoShipmentStatuses.parse(Map.of())).isNull();
        assertThat(BobGoShipmentStatuses.parse(Map.of("data", Map.of("status", "delivered")))).isEqualTo(ShipmentStatus.DELIVERED);
    }
    @Test void providerDeliveryCompletesOrderWithoutMerchantSteps() {
        BobGoShipmentStatuses.apply(shipment, ShipmentStatus.DELIVERED);
        BobGoShipmentStatuses.syncOrder(shipment);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        BobGoShipmentStatuses.apply(shipment, ShipmentStatus.CREATED);
        BobGoShipmentStatuses.apply(shipment, ShipmentStatus.CANCELLED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }
    @Test void cancellationWaitsForConfirmationAndDoesNotRefundPayment() {
        shipment.setStatus(ShipmentStatus.CANCEL_REQUESTED);
        BobGoShipmentStatuses.apply(shipment, ShipmentStatus.CREATED);
        BobGoShipmentStatuses.syncOrder(shipment);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCEL_REQUESTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        BobGoShipmentStatuses.apply(shipment, ShipmentStatus.CANCELLED);
        BobGoShipmentStatuses.syncOrder(shipment);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        BobGoShipmentStatuses.apply(shipment, ShipmentStatus.IN_TRANSIT);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
    }
    @Test void oldEventsCannotReenableCancellationAfterCollection() {
        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        BobGoShipmentStatuses.apply(shipment, ShipmentStatus.CREATED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }
    @Test void currentTrackingCanCorrectStaleInTransitWithoutChangingPayment() {
        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        BobGoShipmentStatuses.applyTrackingSnapshot(shipment,
                BobGoShipmentStatuses.parse(Map.of("status", "pending-collection")));
        BobGoShipmentStatuses.syncOrder(shipment);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }
    @Test void snapshotsPreserveCancellationClaimsAndTerminalStates() {
        for (var status : new ShipmentStatus[]{ShipmentStatus.CANCEL_REQUESTED,
                ShipmentStatus.CANCELLATION_UNKNOWN, ShipmentStatus.CANCELLED, ShipmentStatus.DELIVERED}) {
            shipment.setStatus(status);
            BobGoShipmentStatuses.applyTrackingSnapshot(shipment, ShipmentStatus.CREATED);
            assertThat(shipment.getStatus()).isEqualTo(status);
        }
    }
}
