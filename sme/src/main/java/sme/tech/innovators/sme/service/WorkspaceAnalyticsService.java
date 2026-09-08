package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.response.AnalyticsBreakdownsDto;
import sme.tech.innovators.sme.dto.response.AnalyticsSummaryDto;
import sme.tech.innovators.sme.dto.response.AnalyticsTimeseriesDto;
import sme.tech.innovators.sme.entity.ProductStatus;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.exception.InvalidAnalyticsQueryException;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.ProductRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceAnalyticsService {

    /** Inclusive low-stock band: 1..threshold units (0 is out of stock). */
    public static final int LOW_STOCK_THRESHOLD = 5;
    static final int TOP_PRODUCTS_LIMIT = 10;
    static final int MAX_RANGE_DAYS = 366;
    static final int DEFAULT_RANGE_DAYS = 30;

    private final WorkspaceRepository workspaceRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto summary(UUID workspaceId, UUID userId, LocalDate from, LocalDate to) {
        loadOwnedWorkspace(workspaceId, userId);
        DateRange range = resolveRange(from, to);

        long ordersCount = orderRepository.countOrdersInRange(
                workspaceId, range.fromInclusive(), range.toExclusive());
        Object[] paid = unwrapAggregate(orderRepository.sumPaidRevenueAndCount(
                workspaceId, range.fromInclusive(), range.toExclusive()));
        BigDecimal revenueRaw = toBigDecimal(paid[0]);
        long ordersPaidCount = toLong(paid[1]);

        BigDecimal revenuePaid = revenueRaw.setScale(2, RoundingMode.HALF_UP);
        BigDecimal aov = ordersPaidCount == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : revenuePaid.divide(BigDecimal.valueOf(ordersPaidCount), 2, RoundingMode.HALF_UP);

        String currency = orderRepository.findLatestPaidCurrency(
                        workspaceId, range.fromInclusive(), range.toExclusive())
                .filter(c -> c != null && !c.isBlank())
                .orElse("ZAR");

        return AnalyticsSummaryDto.builder()
                .revenuePaid(revenuePaid)
                .ordersCount(ordersCount)
                .ordersPaidCount(ordersPaidCount)
                .averageOrderValue(aov)
                .productsPublished(productRepository.countByWorkspaceIdAndStatus(
                        workspaceId, ProductStatus.ACTIVE))
                .lowStockCount(productRepository.countLowStock(workspaceId, LOW_STOCK_THRESHOLD))
                .outOfStockCount(productRepository.countOutOfStock(workspaceId))
                .currency(currency)
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsTimeseriesDto timeseries(UUID workspaceId,
                                             UUID userId,
                                             LocalDate from,
                                             LocalDate to,
                                             String grain) {
        loadOwnedWorkspace(workspaceId, userId);
        DateRange range = resolveRange(from, to);
        requireGrainDay(grain);

        List<Object[]> rows = orderRepository.aggregatePaidTimeseriesByDay(
                workspaceId, range.fromInclusive(), range.toExclusive());
        Map<LocalDate, Object[]> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = toLocalDate(row[0]);
            byDay.put(day, new Object[]{row[1], row[2]});
        }

        List<AnalyticsTimeseriesDto.Point> points = new ArrayList<>();
        for (LocalDate d = range.fromDate(); !d.isAfter(range.toDate()); d = d.plusDays(1)) {
            Object[] vals = byDay.getOrDefault(d, new Object[]{BigDecimal.ZERO, 0L});
            points.add(AnalyticsTimeseriesDto.Point.builder()
                    .date(d.toString())
                    .revenue(toBigDecimal(vals[0]).setScale(2, RoundingMode.HALF_UP))
                    .orders(toLong(vals[1]))
                    .build());
        }
        return AnalyticsTimeseriesDto.builder().points(points).build();
    }

    @Transactional(readOnly = true)
    public AnalyticsBreakdownsDto breakdowns(UUID workspaceId, UUID userId, LocalDate from, LocalDate to) {
        loadOwnedWorkspace(workspaceId, userId);
        DateRange range = resolveRange(from, to);

        List<AnalyticsBreakdownsDto.StatusCount> byStatus = new ArrayList<>();
        for (Object[] row : orderRepository.countOrdersByStatus(
                workspaceId, range.fromInclusive(), range.toExclusive())) {
            byStatus.add(AnalyticsBreakdownsDto.StatusCount.builder()
                    .key(toStatusKey(row[0]))
                    .count(toLong(row[1]))
                    .build());
        }

        List<AnalyticsBreakdownsDto.StatusCount> byPayment = new ArrayList<>();
        for (Object[] row : orderRepository.countOrdersByPaymentStatus(
                workspaceId, range.fromInclusive(), range.toExclusive())) {
            byPayment.add(AnalyticsBreakdownsDto.StatusCount.builder()
                    .key(toStatusKey(row[0]))
                    .count(toLong(row[1]))
                    .build());
        }

        List<AnalyticsBreakdownsDto.TopProduct> topProducts = new ArrayList<>();
        for (Object[] row : orderRepository.topProductsByPaidRevenue(
                workspaceId, range.fromInclusive(), range.toExclusive(), TOP_PRODUCTS_LIMIT)) {
            topProducts.add(AnalyticsBreakdownsDto.TopProduct.builder()
                    .productId(toUuidOrNull(row[0]))
                    .title(row[1] != null ? String.valueOf(row[1]) : "Product")
                    .unitsSold(toLong(row[2]))
                    .revenue(toBigDecimal(row[3]).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        List<AnalyticsBreakdownsDto.CategoryRevenue> byCategory = new ArrayList<>();
        for (Object[] row : orderRepository.revenueByCategory(
                workspaceId, range.fromInclusive(), range.toExclusive())) {
            byCategory.add(AnalyticsBreakdownsDto.CategoryRevenue.builder()
                    .categoryId(toUuidOrNull(row[0]))
                    .name(row[1] != null ? String.valueOf(row[1]) : "Uncategorized")
                    .revenue(toBigDecimal(row[2]).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return AnalyticsBreakdownsDto.builder()
                .ordersByStatus(byStatus)
                .ordersByPaymentStatus(byPayment)
                .topProducts(topProducts)
                .revenueByCategory(byCategory)
                .build();
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    public static DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new InvalidAnalyticsQueryException("'from' must be on or before 'to'");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new InvalidAnalyticsQueryException(
                    "Date range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
        return new DateRange(
                start,
                end,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay());
    }

    private static void requireGrainDay(String grain) {
        if (grain == null || grain.isBlank()) {
            return;
        }
        if (!"day".equalsIgnoreCase(grain.trim())) {
            throw new InvalidAnalyticsQueryException(
                    "Unsupported grain '" + grain + "'. Only 'day' is supported.");
        }
    }

    /**
     * Amounts are now stored in major units (e.g. 300.00 = R300.00).
     * This method is kept for API compatibility but simply scales to 2 decimal places.
     * @deprecated Use {@link #toBigDecimal(Object)} directly.
     */
    @Deprecated
    public static BigDecimal toMajor(long majorUnits) {
        return BigDecimal.valueOf(majorUnits).setScale(2, RoundingMode.HALF_UP);
    }

    /** Converts a raw DB aggregate value (BigDecimal, Long, Integer, or null) to BigDecimal. */
    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static Object[] unwrapAggregate(Object raw) {
        if (raw == null) {
            return new Object[]{0L, 0L};
        }
        if (raw instanceof Object[] arr) {
            if (arr.length == 1 && arr[0] instanceof Object[] nested) {
                return nested;
            }
            return arr;
        }
        return new Object[]{0L, 0L};
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof LocalDateTime dt) {
            return dt.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static UUID toUuidOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private static String toStatusKey(Object value) {
        if (value == null) {
            return "unknown";
        }
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    public record DateRange(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive) {}
}
