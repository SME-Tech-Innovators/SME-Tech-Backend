package sme.tech.innovators.sme.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sme.tech.innovators.sme.entity.Product;
import sme.tech.innovators.sme.entity.ProductStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndWorkspaceId(UUID productId, UUID workspaceId);

    boolean existsByWorkspaceIdAndSkuIgnoreCase(UUID workspaceId, String sku);

    boolean existsByWorkspaceIdAndSkuIgnoreCaseAndIdNot(UUID workspaceId, String sku, UUID excludeId);

    boolean existsByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    boolean existsByWorkspaceIdAndSlugAndIdNot(UUID workspaceId, String slug, UUID excludeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products
            SET quantity_available = quantity_available - :qty
            WHERE id = :productId
              AND quantity_available >= :qty
            """, nativeQuery = true)
    int decrementStockIfAvailable(@Param("productId") UUID productId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products
            SET quantity_available = quantity_available + :qty,
                out_of_stock_notified_at = CASE
                    WHEN quantity_available + :qty > 0 THEN NULL
                    ELSE out_of_stock_notified_at
                END
            WHERE id = :productId
            """, nativeQuery = true)
    int incrementStock(@Param("productId") UUID productId, @Param("qty") int qty);

    /**
     * Atomically claim the merchant out-of-stock email for this sold-out episode.
     * Succeeds only when stock is 0 and no notification has been recorded yet.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products
            SET out_of_stock_notified_at = CURRENT_TIMESTAMP
            WHERE id = :productId
              AND quantity_available = 0
              AND out_of_stock_notified_at IS NULL
            """, nativeQuery = true)
    int claimOutOfStockNotification(@Param("productId") UUID productId);

    /** Release a claim so a failed send can be retried on the next sold-out trigger. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products
            SET out_of_stock_notified_at = NULL
            WHERE id = :productId
            """, nativeQuery = true)
    int clearOutOfStockNotification(@Param("productId") UUID productId);

    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.workspace w
            JOIN FETCH w.business b
            JOIN FETCH b.owner
            WHERE p.id = :productId
            """)
    Optional<Product> findByIdWithWorkspaceOwner(@Param("productId") UUID productId);

    /** Lines used for stock movement — bypasses LAZY Product association. */
    @Query(value = """
            SELECT product_id, quantity
            FROM order_items
            WHERE order_id = :orderId
              AND product_id IS NOT NULL
              AND quantity > 0
            """, nativeQuery = true)
    List<Object[]> findStockLinesForOrder(@Param("orderId") UUID orderId);

    @Query("""
            SELECT p FROM Product p
            LEFT JOIN p.category c
            WHERE p.workspace.id = :workspaceId
              AND (:status IS NULL OR p.status = :status)
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (
                   :search IS NULL OR :search = ''
                   OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
              )
              AND (
                   :onSale IS NULL
                   OR (
                        :onSale = TRUE
                        AND p.compareAtPriceAmount IS NOT NULL
                        AND p.compareAtPriceAmount > p.priceAmount
                   )
                   OR (
                        :onSale = FALSE
                        AND (
                             p.compareAtPriceAmount IS NULL
                             OR p.compareAtPriceAmount <= p.priceAmount
                        )
                   )
              )
              AND (
                   :inStock IS NULL
                   OR (:inStock = TRUE AND p.quantityAvailable > 0)
                   OR (:inStock = FALSE AND p.quantityAvailable <= 0)
              )
            """)
    Page<Product> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") ProductStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("search") String search,
            @Param("onSale") Boolean onSale,
            @Param("inStock") Boolean inStock,
            Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            LEFT JOIN p.category c
            WHERE p.workspace.id = :workspaceId
              AND p.status = sme.tech.innovators.sme.entity.ProductStatus.ACTIVE
              AND (
                   :category IS NULL OR :category = ''
                   OR LOWER(c.slug) = LOWER(:category)
                   OR LOWER(c.name) = LOWER(:category)
              )
              AND (
                   :search IS NULL OR :search = ''
                   OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
              )
              AND (
                   :onSale IS NULL
                   OR (
                        :onSale = TRUE
                        AND p.compareAtPriceAmount IS NOT NULL
                        AND p.compareAtPriceAmount > p.priceAmount
                   )
                   OR (
                        :onSale = FALSE
                        AND (
                             p.compareAtPriceAmount IS NULL
                             OR p.compareAtPriceAmount <= p.priceAmount
                        )
                   )
              )
            """)
    Page<Product> searchPublic(
            @Param("workspaceId") UUID workspaceId,
            @Param("category") String category,
            @Param("search") String search,
            @Param("onSale") Boolean onSale,
            Pageable pageable);

    Optional<Product> findByWorkspaceIdAndSlugAndStatus(UUID workspaceId, String slug, ProductStatus status);

    Optional<Product> findByWorkspaceIdAndIdAndStatus(UUID workspaceId, UUID productId, ProductStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, ProductStatus status);

    @Query("""
            SELECT COUNT(p)
            FROM Product p
            WHERE p.workspace.id = :workspaceId
              AND p.quantityAvailable > 0
              AND p.quantityAvailable <= :threshold
            """)
    long countLowStock(
            @Param("workspaceId") UUID workspaceId,
            @Param("threshold") int threshold);

    @Query("""
            SELECT COUNT(p)
            FROM Product p
            WHERE p.workspace.id = :workspaceId
              AND p.quantityAvailable <= 0
            """)
    long countOutOfStock(@Param("workspaceId") UUID workspaceId);
}
