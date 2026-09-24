package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.OrderShipment;

import java.util.Optional;
import java.util.UUID;

public interface OrderShipmentRepository extends JpaRepository<OrderShipment, UUID> {

    Optional<OrderShipment> findByOrderId(UUID orderId);

    Optional<OrderShipment> findByOrderIdAndWorkspaceId(UUID orderId, UUID workspaceId);

    Optional<OrderShipment> findByBobgoShipmentId(String bobgoShipmentId);

    Optional<OrderShipment> findByTrackingReference(String trackingReference);
}
