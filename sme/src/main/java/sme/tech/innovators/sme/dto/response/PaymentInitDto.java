package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitDto {
    private String authorizationUrl;
    private String accessCode;
    private String reference;
    private String publicKey;
    private String orderId;
    private Integer amount;
    private String currency;
}
