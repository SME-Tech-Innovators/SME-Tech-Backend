package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemDto {
    private String id;
    private String orderId;
    private String productId;
    private String title;
    private String sku;
    private Integer quantity;
    private BigDecimal unitPriceAmount;
    private BigDecimal totalAmount;
    private String currency;
}
