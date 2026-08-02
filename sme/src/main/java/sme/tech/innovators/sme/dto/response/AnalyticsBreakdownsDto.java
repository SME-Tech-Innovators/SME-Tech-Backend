package sme.tech.innovators.sme.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsBreakdownsDto {

    @Builder.Default
    private List<StatusCount> ordersByStatus = new ArrayList<>();

    @Builder.Default
    private List<StatusCount> ordersByPaymentStatus = new ArrayList<>();

    @Builder.Default
    private List<TopProduct> topProducts = new ArrayList<>();

    @Builder.Default
    private List<CategoryRevenue> revenueByCategory = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {
        /** Lowercase enum name, e.g. paid / pending_payment */
        private String key;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProduct {
        private UUID productId;
        private String title;
        private long unitsSold;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRevenue {
        private UUID categoryId;
        private String name;
        private BigDecimal revenue;
    }
}
