package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CartDto {
    private String id;
    private String workspaceId;
    private String status;
    private String currency;
    private List<CartItemDto> items;
    private Integer subtotalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
