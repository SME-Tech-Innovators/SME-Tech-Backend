package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.UpdateOrderStatusRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.OrderConfirmationDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.MerchantOrderService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Workspace Orders", description = "Merchant order list, detail, and fulfilment status")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;
    private final UserRepository userRepository;

    @Operation(summary = "List orders for workspace (newest first)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderConfirmationDto>>> listOrders(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantOrderService.listOrders(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Get a single order")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderConfirmationDto>> getOrder(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantOrderService.getOrder(workspaceId, resolveUserId(auth), orderId)));
    }

    @Operation(summary = "Update fulfilment status (processing | fulfilled | cancelled)")
    @PatchMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderConfirmationDto>> updateOrderStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantOrderService.updateOrderStatus(
                        workspaceId, resolveUserId(auth), orderId, request.getStatus())));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
