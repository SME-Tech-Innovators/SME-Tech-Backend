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

    @Query("""
            SELECT COUNT(o)
            FROM Order o
            WHERE o.workspace.id = :workspaceId
              AND o.createdAt >= :fromInclusive
              AND o.createdAt < :toExclusive
            """)
    long countOrdersInRange(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0), COUNT(o)
            FROM Order o
            WHERE o.workspace.id = :workspaceId
              AND o.paymentStatus = sme.tech.innovators.sme.entity.PaymentStatus.PAID
              AND o.createdAt >= :fromInclusive
              AND o.createdAt < :toExclusive
            """)
    Object[] sumPaidRevenueAndCount(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive);

    @Query(value = """
            SELECT o.currency
            FROM orders o
            WHERE o.workspace_id = :workspaceId
              AND o.payment_status = 'PAID'
              AND o.created_at >= :fromInclusive
              AND o.created_at < :toExclusive
            ORDER BY o.created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findLatestPaidCurrency(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive);

    @Query(value = """
            SELECT CAST(o.created_at AS date) AS day,
                   COALESCE(SUM(CASE WHEN o.payment_status = 'PAID' THEN o.total_amount ELSE 0 END), 0) AS revenue_minor,
                   COUNT(*) FILTER (WHERE o.payment_status = 'PAID') AS paid_orders
            FROM orders o
            WHERE o.workspace_id = :workspaceId
              AND o.created_at >= :fromInclusive
              AND o.created_at < :toExclusive
            GROUP BY CAST(o.created_at AS date)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> aggregatePaidTimeseriesByDay(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive);

    @Query(value = """
            SELECT o.status, COUNT(*)
            FROM orders o
            WHERE o.workspace_id = :workspaceId
              AND o.created_at >= :fromInclusive
              AND o.created_at < :toExclusive
            GROUP BY o.status
            """, nativeQuery = true)
    List<Object[]> countOrdersByStatus(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive);

    @Query(value = """
            SELECT o.payment_status, COUNT(*)
            FROM orders o
            WHERE o.workspace_id = :workspaceId
              AND o.created_at >= :fromInclusive
              AND o.created_at < :toExclusive
            GROUP BY o.payment_status
            """, nativeQuery = true)
    List<Object[]> countOrdersByPaymentStatus(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive);

    @Query(value = """
            SELECT oi.product_id,
                   MAX(oi.title) AS title,
                   COALESCE(SUM(oi.quantity), 0) AS units,
                   COALESCE(SUM(oi.total_amount), 0) AS revenue_minor
            FROM order_items oi
            INNER JOIN orders o ON o.id = oi.order_id
            WHERE o.workspace_id = :workspaceId
              AND o.payment_status = 'PAID'
              AND o.created_at >= :fromInclusive
              AND o.created_at < :toExclusive
            GROUP BY oi.product_id
            ORDER BY revenue_minor DESC, units DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topProductsByPaidRevenue(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive,
            @Param("limit") int limit);

    @Query(value = """
            SELECT c.id,
                   COALESCE(c.name, 'Uncategorized') AS name,
                   COALESCE(SUM(oi.total_amount), 0) AS revenue_minor
            FROM order_items oi
            INNER JOIN orders o ON o.id = oi.order_id
            LEFT JOIN products p ON p.id = oi.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE o.workspace_id = :workspaceId
              AND o.payment_status = 'PAID'
              AND o.created_at >= :fromInclusive
              AND o.created_at < :toExclusive
            GROUP BY c.id, c.name
            ORDER BY revenue_minor DESC
            """, nativeQuery = true)
    List<Object[]> revenueByCategory(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive);
}
