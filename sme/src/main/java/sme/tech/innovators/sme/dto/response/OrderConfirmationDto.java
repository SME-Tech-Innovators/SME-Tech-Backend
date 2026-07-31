package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class OrderConfirmationDto {
    private String id;
    private String workspaceId;
    private String cartId;
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Map<String, Object> shippingAddress;
    private List<OrderItemDto> items;
    private Integer subtotalAmount;
    private Integer shippingAmount;
    private Integer totalAmount;
    private String currency;
    private String status;
    private String paymentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
