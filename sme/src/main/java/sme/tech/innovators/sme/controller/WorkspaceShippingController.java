package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.UpdateShippingSettingsRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.ShippingSettingsDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.WorkspaceShippingSettingsService;

import java.util.UUID;

@Tag(name = "Workspace Shipping", description = "Bob Go shipping settings")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/shipping")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class WorkspaceShippingController {

    private final WorkspaceShippingSettingsService shippingSettingsService;
    private final UserRepository userRepository;

    @Operation(summary = "Get shipping settings")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<ShippingSettingsDto>> getSettings(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingSettingsService.getSettings(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Update shipping settings")
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<ShippingSettingsDto>> updateSettings(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateShippingSettingsRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingSettingsService.updateSettings(workspaceId, resolveUserId(auth), request)));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
