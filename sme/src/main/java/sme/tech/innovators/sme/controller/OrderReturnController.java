package sme.tech.innovators.sme.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.ReturnQuoteRequest;
import sme.tech.innovators.sme.dto.response.*;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.OrderReturnService;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/orders/{orderId}/return")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Order Returns", description = "Full-order Bob Go returns and Paystack refunds")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "Bearer Authentication")
public class OrderReturnController {
    private final OrderReturnService service;
    private final UserRepository users;

    @GetMapping
    public ApiResponse<OrderReturnDto> get(@PathVariable UUID workspaceId, @PathVariable UUID orderId, Authentication auth) {
        return ApiResponse.success(service.get(workspaceId, user(auth), orderId));
    }
    @PostMapping("/quote")
    public ApiResponse<OrderReturnDto> quote(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
            Authentication auth, @Valid @RequestBody ReturnQuoteRequest request) {
        return ApiResponse.success(service.quote(workspaceId, user(auth), orderId, request));
    }
    @PostMapping("/shipment")
    public ApiResponse<OrderReturnDto> shipment(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
            Authentication auth, @Valid @RequestBody ShipmentRequest request) {
        return ApiResponse.success(service.createShipment(workspaceId, user(auth), orderId, request.optionId()));
    }
    @PostMapping("/receive")
    public ApiResponse<OrderReturnDto> receive(@PathVariable UUID workspaceId, @PathVariable UUID orderId, Authentication auth) {
        return ApiResponse.success(service.receive(workspaceId, user(auth), orderId));
    }
    @PostMapping("/refund")
    public ApiResponse<OrderReturnDto> refund(@PathVariable UUID workspaceId, @PathVariable UUID orderId, Authentication auth) {
        return ApiResponse.success(service.refund(workspaceId, user(auth), orderId));
    }
    @PostMapping("/refund/refresh")
    public ApiResponse<OrderReturnDto> refresh(@PathVariable UUID workspaceId, @PathVariable UUID orderId, Authentication auth) {
        return ApiResponse.success(service.refreshRefund(workspaceId, user(auth), orderId));
    }
    private UUID user(Authentication auth) {
        return users.findByEmailAndIsDeletedFalse(auth.getName())
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found")).getId();
    }
    public record ShipmentRequest(@NotBlank String optionId) {}
}
