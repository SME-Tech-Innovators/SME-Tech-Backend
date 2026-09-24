package sme.tech.innovators.sme.dto.response;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
public record OrderReturnDto(UUID orderId, String shipmentStatus, String refundStatus,
    String trackingReference, String shipmentId, String refundId, LocalDateTime receivedAt,
    List<ShippingQuoteDto.ShippingOptionDto> options) {}
