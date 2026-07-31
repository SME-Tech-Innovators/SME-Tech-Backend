package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sme.tech.innovators.sme.entity.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Order> findAllByWorkspace_IdOrderByCreatedAtDesc(UUID workspaceId);

    boolean existsByOrderNumber(String orderNumber);

    @Query("""
            SELECT o FROM Order o
            WHERE o.workspace.id = :workspaceId
              AND LOWER(o.orderNumber) = LOWER(:orderNumber)
              AND o.customerEmail IS NOT NULL
              AND LOWER(o.customerEmail) = LOWER(:email)
            """)
    Optional<Order> findByWorkspaceIdAndOrderNumberIgnoreCaseAndCustomerEmailIgnoreCase(
            @Param("workspaceId") UUID workspaceId,
            @Param("orderNumber") String orderNumber,
            @Param("email") String email);

    /** Eager lines + products for stock decrement after payment. */
    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithItemsAndProducts(@Param("id") UUID id);
}
