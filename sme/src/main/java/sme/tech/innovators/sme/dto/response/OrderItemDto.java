package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemDto {
    private String id;
    private String orderId;
    private String productId;
    private String title;
    private String sku;
    private Integer quantity;
    private Integer unitPriceAmount;
    private Integer totalAmount;
    private String currency;
}
