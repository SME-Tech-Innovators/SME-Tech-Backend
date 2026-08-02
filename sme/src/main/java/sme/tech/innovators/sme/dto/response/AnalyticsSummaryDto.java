package sme.tech.innovators.sme.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDto {
    private BigDecimal revenuePaid;
    private long ordersCount;
    private long ordersPaidCount;
    private BigDecimal averageOrderValue;
    private long productsPublished;
    private long lowStockCount;
    private long outOfStockCount;
    private String currency;
}
