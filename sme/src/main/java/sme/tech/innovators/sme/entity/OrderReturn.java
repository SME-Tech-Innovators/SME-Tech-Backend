package sme.tech.innovators.sme.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "order_returns")
@Getter @Setter @NoArgsConstructor
public class OrderReturn {
    @Id private UUID orderId;
    @Column(nullable = false) private String shipmentStatus = "QUOTED";
    @Column(nullable = false) private String refundStatus = "NOT_REQUESTED";
    @Column(nullable = false, length = 1000) private String reason;
    private String trackingReference;
    private String shipmentId;
    private String refundId;
    private UUID paymentId;
    private LocalDateTime receivedAt;
    private LocalDateTime quotedAt;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> shipmentPayload;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> rates;
}
