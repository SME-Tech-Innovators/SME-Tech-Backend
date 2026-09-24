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
import sme.tech.innovators.sme.dto.response.OrderShippingStatusDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.BobGoShipmentService;
import sme.tech.innovators.sme.service.MerchantOrderService;
import sme.tech.innovators.sme.service.OrderShippingService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Workspace Orders", description = "Merchant order list, detail, and fulfilment status")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class MerchantOrderController {

    private final sme.tech.innovators.sme.service.OrderCancellationRequestService cancellationRequests;
    private final MerchantOrderService merchantOrderService;
    private final OrderShippingService orderShippingService;
    private final BobGoShipmentService bobGoShipmentService;
    private final UserRepository userRepository;
    private final sme.tech.innovators.sme.service.BobGoCancellationService bobGoCancellationService;

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

    @Operation(summary = "Get order shipping / tracking status")
    @GetMapping("/{orderId}/shipping")
    public ResponseEntity<ApiResponse<OrderShippingStatusDto>> getOrderShipping(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                orderShippingService.getMerchantShipping(workspaceId, resolveUserId(auth), orderId)));
    }

    @PostMapping("/{orderId}/shipping/refresh")
    public ResponseEntity<ApiResponse<OrderShippingStatusDto>> refreshShipping(
            @PathVariable UUID workspaceId, @PathVariable UUID orderId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                orderShippingService.refreshTracking(workspaceId, resolveUserId(auth), orderId)));
    }

    @PostMapping("/{orderId}/shipping/cancel")
    public ResponseEntity<ApiResponse<OrderShippingStatusDto>> cancelShipping(
            @PathVariable UUID workspaceId, @PathVariable UUID orderId, Authentication auth) {
        UUID userId = resolveUserId(auth);
        bobGoCancellationService.cancel(workspaceId, userId, orderId);
        return ResponseEntity.ok(ApiResponse.success(
                orderShippingService.getMerchantShipping(workspaceId, userId, orderId)));
    }

    @Operation(summary = "Create Bob Go shipment for a paid order")
    @PostMapping("/{orderId}/shipping/create-shipment")
    public ResponseEntity<ApiResponse<OrderShippingStatusDto>> createShipment(
            @PathVariable UUID workspaceId,
            @PathVariable UUID orderId,
            Authentication auth) {
        merchantOrderService.getOrder(workspaceId, resolveUserId(auth), orderId);
        bobGoShipmentService.createShipmentForMerchant(workspaceId, orderId);
        return ResponseEntity.ok(ApiResponse.success(
                orderShippingService.getMerchantShipping(workspaceId, resolveUserId(auth), orderId)));
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

    @GetMapping("/{orderId}/cancellation-request")
    public ResponseEntity<ApiResponse<sme.tech.innovators.sme.dto.response.OrderCancellationDto>> cancellationRequest(
            @PathVariable UUID workspaceId, @PathVariable UUID orderId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                cancellationRequests.merchantStatus(workspaceId, resolveUserId(auth), orderId)));
    }

    @PostMapping("/{orderId}/cancellation-request/review")
    public ResponseEntity<ApiResponse<sme.tech.innovators.sme.dto.response.OrderCancellationDto>> reviewCancellation(
            @PathVariable UUID workspaceId, @PathVariable UUID orderId, Authentication auth,
            @Valid @RequestBody sme.tech.innovators.sme.dto.request.ReviewOrderCancellation request) {
        return ResponseEntity.ok(ApiResponse.success(cancellationRequests.review(
                workspaceId, resolveUserId(auth), orderId, request.decision(), request.note())));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
