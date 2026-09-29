package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.CreateDeliveryRequest;
import sme.tech.innovators.sme.dto.request.UpdateDeliveryRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.DeliveryDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.DeliveryService;

import java.util.UUID;

@Tag(name = "Order Delivery", description = "Merchant delivery details and tracking status")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/orders/{orderId}/delivery")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final UserRepository userRepository;

    @Operation(summary = "Create delivery details for an order")
    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryDto>> create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateDeliveryRequest request,
            Authentication auth) {
        DeliveryDto delivery = deliveryService.create(
                workspaceId, resolveUserId(auth), orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(delivery));
    }

    @Operation(summary = "Get delivery details and event history")
    @GetMapping
    public ResponseEntity<ApiResponse<DeliveryDto>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.get(
                workspaceId, resolveUserId(auth), orderId)));
    }

    @Operation(summary = "Update delivery status or tracking metadata")
    @PatchMapping
    public ResponseEntity<ApiResponse<DeliveryDto>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateDeliveryRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.update(
                workspaceId, resolveUserId(auth), orderId, request)));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}