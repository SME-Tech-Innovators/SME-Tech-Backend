package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentInitDto {
    private String authorizationUrl;
    private String accessCode;
    private String reference;
    private String publicKey;
    private String orderId;
    private BigDecimal amount;
    private String currency;
}
