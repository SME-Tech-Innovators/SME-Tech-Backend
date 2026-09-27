package sme.tech.innovators.sme.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.UpdateDeliverySettingsRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.DeliverySettingsDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.UberDirectService;
import sme.tech.innovators.sme.service.WorkspaceDeliverySettingsService;

import java.util.UUID;

/**
 * Merchant-facing endpoints for managing workspace delivery settings
 * and dispatching Uber Direct deliveries for paid orders.
 *
 * <p>All endpoints require a valid Bearer token and workspace ownership.
 */
@Tag(name = "Workspace Delivery Settings",
     description = "Manage delivery provider configuration and dispatch Uber Direct deliveries")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/delivery-settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class WorkspaceDeliverySettingsController {

    private final WorkspaceDeliverySettingsService settingsService;
    private final UberDirectService uberDirectService;
    private final UserRepository userRepository;

    // ── Settings CRUD ─────────────────────────────────────────────────────

    @Operation(summary = "Get workspace delivery settings",
               description = "Returns the current delivery configuration including which providers "
                             + "are enabled and the pickup address for this workspace.")
    @GetMapping
    public ResponseEntity<ApiResponse<DeliverySettingsDto>> getSettings(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                settingsService.getSettings(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Update workspace delivery settings",
               description = "Enable or disable Uber Direct and set the pickup address. "
                             + "Creates the settings record on first call.")
    @PutMapping
    public ResponseEntity<ApiResponse<DeliverySettingsDto>> updateSettings(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateDeliverySettingsRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                settingsService.updateSettings(workspaceId, resolveUserId(auth), request)));
    }

    // ── Uber Direct dispatch ──────────────────────────────────────────────

    @Operation(summary = "Book an Uber Direct delivery for a paid order",
               description = "Dispatches an on-demand Uber Direct courier for the specified order "
                             + "and persists the delivery record. The order must be in PAID status. "
                             + "Pass the quoteId received at checkout for price consistency, or "
                             + "omit it to let Uber auto-select.")
    @PostMapping("/uber-direct/orders/{orderId}/book")
    public ResponseEntity<ApiResponse<JsonNode>> bookUberDirectDelivery(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            @RequestParam(required = false) String quoteId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                uberDirectService.bookDelivery(
                        workspaceId, resolveUserId(auth), orderId, quoteId)));
    }

    @Operation(summary = "Refresh Uber Direct delivery status",
               description = "Polls Uber Direct for the latest status of the delivery linked "
                             + "to this order and updates the stored delivery record.")
    @PostMapping("/uber-direct/orders/{orderId}/refresh-status")
    public ResponseEntity<ApiResponse<JsonNode>> refreshUberDirectStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                uberDirectService.refreshDeliveryStatus(
                        workspaceId, resolveUserId(auth), orderId)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID resolveUserId(Authentication auth) {
        return userRepository.findByEmailAndIsDeletedFalse(auth.getName())
                .map(User::getId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Authenticated user not found: " + auth.getName()));
    }
}
