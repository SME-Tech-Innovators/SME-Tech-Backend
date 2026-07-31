package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePaymentSettingsRequest {

    @Size(max = 255)
    private String payoutBusinessName;

    @Size(max = 20)
    private String payoutBankCode;

    @Size(max = 50)
    private String payoutAccountNumber;

    @Min(0)
    @Max(100)
    private Integer platformFeePercent;
}
