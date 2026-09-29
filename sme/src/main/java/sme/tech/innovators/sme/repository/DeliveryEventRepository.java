package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.DeliveryEvent;

import java.util.List;
import java.util.UUID;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {
    List<DeliveryEvent> findAllByDeliveryIdOrderByCreatedAtAsc(UUID deliveryId);
}
