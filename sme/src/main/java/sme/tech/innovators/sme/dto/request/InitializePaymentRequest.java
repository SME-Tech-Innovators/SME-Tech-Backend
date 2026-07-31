package sme.tech.innovators.sme.dto.request;

import lombok.Data;

@Data
public class InitializePaymentRequest {
    /** Optional frontend return URL after Paystack Popup / redirect. */
    private String callbackUrl;
}
