package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CartItemDto {
    private String id;
    private String cartId;
    private String productId;
    private String productTitle;
    private String productSlug;
    private String productImageUrl;
    private Integer quantity;
    private BigDecimal unitPriceAmount;
    private BigDecimal lineTotalAmount;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
