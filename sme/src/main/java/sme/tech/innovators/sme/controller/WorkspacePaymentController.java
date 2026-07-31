package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.UpdatePaymentSettingsRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.PaymentSettingsDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.PaymentSettingsService;

import java.util.UUID;

@Tag(name = "Workspace Payments", description = "Merchant payout settings and Paystack subaccount connect")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class WorkspacePaymentController {

    private final PaymentSettingsService paymentSettingsService;
    private final UserRepository userRepository;

    @Operation(summary = "Get payment / payout settings")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<PaymentSettingsDto>> getSettings(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentSettingsService.getSettings(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Update payout bank details (draft)")
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<PaymentSettingsDto>> updateSettings(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdatePaymentSettingsRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentSettingsService.updateSettings(workspaceId, resolveUserId(auth), request)));
    }

    @Operation(summary = "Create or update Paystack subaccount for this workspace")
    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<PaymentSettingsDto>> connect(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentSettingsService.connect(workspaceId, resolveUserId(auth))));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
