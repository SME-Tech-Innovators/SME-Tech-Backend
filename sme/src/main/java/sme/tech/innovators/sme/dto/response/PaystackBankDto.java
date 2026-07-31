package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaystackBankDto {
    private String name;
    private String code;
    private String country;
    private String currency;
}
