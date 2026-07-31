package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentSettingsDto {
    private String workspaceId;
    private String payoutBusinessName;
    private String payoutBankCode;
    private String payoutAccountNumber;
    private String payoutAccountName;
    private String paystackSubaccountCode;
    /** Lowercase: not_connected | pending | active | failed */
    private String paystackSubaccountStatus;
    private Integer platformFeePercent;
    private Integer effectivePlatformFeePercent;
    /** Platform public key only — never the secret. */
    private String publicKey;
}
