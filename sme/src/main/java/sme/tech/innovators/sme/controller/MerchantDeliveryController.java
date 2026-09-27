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
import sme.tech.innovators.sme.dto.request.DeliveryQuoteRequest;
import sme.tech.innovators.sme.dto.request.CreateShipLogicShipmentRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.ShipLogicQuoteService;
import sme.tech.innovators.sme.service.ShipLogicShipmentService;

import java.util.UUID;

@Tag(name = "Workspace Delivery", description = "Merchant delivery quotes")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/delivery")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class MerchantDeliveryController {

    private final ShipLogicQuoteService shipLogicQuoteService;
        private final ShipLogicShipmentService shipLogicShipmentService;
    private final UserRepository userRepository;

    @Operation(summary = "Get Courier Guy delivery quotes")
    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<JsonNode>> getQuote(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody DeliveryQuoteRequest request,
            Authentication auth
    ) {
        UUID userId = resolveUserId(auth);

        return ResponseEntity.ok(ApiResponse.success(
                shipLogicQuoteService.getQuote(workspaceId, userId, request)
        ));
    }

        @Operation(summary = "Create a Courier Guy shipment")
        @PostMapping("/orders/{orderId}/shipment")
        public ResponseEntity<ApiResponse<JsonNode>> createShipment(
                        @PathVariable UUID workspaceId,
                        @PathVariable UUID orderId,
                        @Valid @RequestBody CreateShipLogicShipmentRequest request,
                        Authentication auth
        ) {
                UUID userId = resolveUserId(auth);
                return ResponseEntity.ok(ApiResponse.success(
                                shipLogicShipmentService.create(workspaceId, userId, orderId, request)
                ));
        }

    private UUID resolveUserId(Authentication auth) {
        User user = userRepository.findByEmailAndIsDeletedFalse(auth.getName())
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Authenticated user not found: " + auth.getName()
                ));
        return user.getId();
    }
}
