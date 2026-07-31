package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.PaystackBankDto;
import sme.tech.innovators.sme.service.PaymentService;
import sme.tech.innovators.sme.service.PaymentSettingsService;

import java.util.List;
import java.util.Map;

@Tag(name = "Paystack", description = "Platform Paystack helpers (banks list + webhook)")
@RestController
@RequestMapping("/api/v1/payments/paystack")
@RequiredArgsConstructor
public class PaystackController {

    private final PaymentSettingsService paymentSettingsService;
    private final PaymentService paymentService;

    @Operation(summary = "List Paystack banks for payout dropdown")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/banks")
    public ResponseEntity<ApiResponse<List<PaystackBankDto>>> listBanks(
            @RequestParam(defaultValue = "ZA") String country) {
        return ResponseEntity.ok(ApiResponse.success(paymentSettingsService.listBanks(country)));
    }

    @Operation(summary = "Paystack webhook receiver")
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Boolean>> webhook(
            @RequestHeader(value = "x-paystack-signature", required = false) String signature,
            @RequestBody String rawBody) {
        paymentService.handleWebhook(signature, rawBody);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
