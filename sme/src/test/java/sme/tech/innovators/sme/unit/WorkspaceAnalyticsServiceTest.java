package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sme.tech.innovators.sme.dto.response.AnalyticsBreakdownsDto;
import sme.tech.innovators.sme.dto.response.AnalyticsSummaryDto;
import sme.tech.innovators.sme.dto.response.AnalyticsTimeseriesDto;
import sme.tech.innovators.sme.entity.ProductStatus;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.exception.InvalidAnalyticsQueryException;
import sme.tech.innovators.sme.repository.OrderRepository;
import sme.tech.innovators.sme.repository.ProductRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import sme.tech.innovators.sme.service.WorkspaceAnalyticsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceAnalyticsServiceTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;

    private WorkspaceAnalyticsService service;
    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new WorkspaceAnalyticsService(workspaceRepository, orderRepository, productRepository);
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        lenient().when(workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId))
                .thenReturn(Optional.of(Workspace.builder().id(workspaceId).name("Shop").build()));
    }

    @Test
    void summary_computesAovAndMajorUnits() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);

        when(orderRepository.countOrdersInRange(eq(workspaceId), any(), any())).thenReturn(48L);
        when(orderRepository.sumPaidRevenueAndCount(eq(workspaceId), any(), any()))
                .thenReturn(new Object[]{1_250_000L, 41L}); // R12,500.00 / 41
        when(orderRepository.findLatestPaidCurrency(eq(workspaceId), any(), any()))
                .thenReturn(Optional.of("ZAR"));
        when(productRepository.countByWorkspaceIdAndStatus(workspaceId, ProductStatus.ACTIVE))
                .thenReturn(22L);
        when(productRepository.countLowStock(workspaceId, WorkspaceAnalyticsService.LOW_STOCK_THRESHOLD))
                .thenReturn(5L);
        when(productRepository.countOutOfStock(workspaceId)).thenReturn(2L);

        AnalyticsSummaryDto dto = service.summary(workspaceId, userId, from, to);

        assertThat(dto.getRevenuePaid()).isEqualByComparingTo("12500.00");
        assertThat(dto.getOrdersCount()).isEqualTo(48);
        assertThat(dto.getOrdersPaidCount()).isEqualTo(41);
        assertThat(dto.getAverageOrderValue()).isEqualByComparingTo("304.88");
        assertThat(dto.getProductsPublished()).isEqualTo(22);
        assertThat(dto.getLowStockCount()).isEqualTo(5);
        assertThat(dto.getOutOfStockCount()).isEqualTo(2);
        assertThat(dto.getCurrency()).isEqualTo("ZAR");
    }

    @Test
    void timeseries_fillsMissingDays() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 3);
        when(orderRepository.aggregatePaidTimeseriesByDay(eq(workspaceId), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{LocalDate.of(2026, 7, 1), 120_000L, 4L},
                        new Object[]{LocalDate.of(2026, 7, 3), 80_000L, 3L}
                ));

        AnalyticsTimeseriesDto dto = service.timeseries(workspaceId, userId, from, to, "day");

        assertThat(dto.getPoints()).hasSize(3);
        assertThat(dto.getPoints().get(0).getDate()).isEqualTo("2026-07-01");
        assertThat(dto.getPoints().get(0).getRevenue()).isEqualByComparingTo("1200.00");
        assertThat(dto.getPoints().get(0).getOrders()).isEqualTo(4);
        assertThat(dto.getPoints().get(1).getDate()).isEqualTo("2026-07-02");
        assertThat(dto.getPoints().get(1).getRevenue()).isEqualByComparingTo("0.00");
        assertThat(dto.getPoints().get(1).getOrders()).isZero();
        assertThat(dto.getPoints().get(2).getOrders()).isEqualTo(3);
    }

    @Test
    void timeseries_rejectsUnsupportedGrain() {
        assertThatThrownBy(() -> service.timeseries(
                workspaceId, userId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), "week"))
                .isInstanceOf(InvalidAnalyticsQueryException.class)
                .hasMessageContaining("grain");
    }

    @Test
    void breakdowns_mapsStatusKeysAndTopProducts() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(orderRepository.countOrdersByStatus(eq(workspaceId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"PAID", 20L}));
        when(orderRepository.countOrdersByPaymentStatus(eq(workspaceId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"PAID", 18L}));
        when(orderRepository.topProductsByPaidRevenue(eq(workspaceId), any(), any(), eq(10)))
                .thenReturn(List.<Object[]>of(new Object[]{productId, "Dress", 12L, 360_000L}));
        when(orderRepository.revenueByCategory(eq(workspaceId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{categoryId, "Apparel", 500_000L}));

        AnalyticsBreakdownsDto dto = service.breakdowns(
                workspaceId, userId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(dto.getOrdersByStatus()).singleElement().satisfies(s -> {
            assertThat(s.getKey()).isEqualTo("paid");
            assertThat(s.getCount()).isEqualTo(20);
        });
        assertThat(dto.getTopProducts()).singleElement().satisfies(p -> {
            assertThat(p.getProductId()).isEqualTo(productId);
            assertThat(p.getTitle()).isEqualTo("Dress");
            assertThat(p.getUnitsSold()).isEqualTo(12);
            assertThat(p.getRevenue()).isEqualByComparingTo("3600.00");
        });
        assertThat(dto.getRevenueByCategory().get(0).getRevenue()).isEqualByComparingTo("5000.00");
    }

    @Test
    void resolveRange_rejectsInvertedDates() {
        assertThatThrownBy(() -> WorkspaceAnalyticsService.resolveRange(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1)))
                .isInstanceOf(InvalidAnalyticsQueryException.class);
    }

    @Test
    void toMajor_convertsCents() {
        assertThat(WorkspaceAnalyticsService.toMajor(12500)).isEqualByComparingTo(new BigDecimal("125.00"));
    }
}
