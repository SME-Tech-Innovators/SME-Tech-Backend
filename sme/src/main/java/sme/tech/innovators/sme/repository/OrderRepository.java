package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByOrderNumber(String orderNumber);
}
