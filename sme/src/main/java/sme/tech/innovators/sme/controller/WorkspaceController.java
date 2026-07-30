package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.PublishStorefrontRequest;
import sme.tech.innovators.sme.dto.request.ResetStorefrontDraftRequest;
import sme.tech.innovators.sme.dto.request.UpdateStorefrontDraftRequest;
import sme.tech.innovators.sme.dto.response.*;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.WorkspaceService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Workspaces", description = "Workspace, storefront draft, and publish management for authenticated merchants")
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;

    @Operation(summary = "List workspaces")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceDto>>> listWorkspaces(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.getWorkspacesForUser(resolveUserId(auth))));
    }

    @Operation(summary = "Get workspace")
    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceDto>> getWorkspace(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.getWorkspace(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Get storefront draft")
    @GetMapping("/{workspaceId}/storefront/draft")
    public ResponseEntity<ApiResponse<StorefrontDraftDto>> getStorefrontDraft(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.getStorefrontDraft(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Update storefront draft")
    @PutMapping("/{workspaceId}/storefront/draft")
    public ResponseEntity<ApiResponse<StorefrontDraftDto>> updateStorefrontDraft(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateStorefrontDraftRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.updateStorefrontDraft(workspaceId, resolveUserId(auth), request)));
    }

    @Operation(summary = "Reset storefront draft")
    @PostMapping("/{workspaceId}/storefront/draft/reset")
    public ResponseEntity<ApiResponse<StorefrontDraftDto>> resetStorefrontDraft(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody ResetStorefrontDraftRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.resetStorefrontDraft(workspaceId, resolveUserId(auth), request)));
    }

    @Operation(summary = "Publish storefront (Go Live)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Published successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Confirmation missing or invalid config"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace/storefront/draft not found")
    })
    @PostMapping("/{workspaceId}/storefront/publish")
    public ResponseEntity<ApiResponse<PublishResultDto>> publishStorefront(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody PublishStorefrontRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.publishStorefront(workspaceId, resolveUserId(auth), request)));
    }

    @Operation(summary = "Get latest published storefront")
    @GetMapping("/{workspaceId}/storefront/published")
    public ResponseEntity<ApiResponse<PublishedStorefrontDto>> getPublishedStorefront(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.getPublishedStorefront(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Get publish history (metadata only)")
    @GetMapping("/{workspaceId}/storefront/publish-history")
    public ResponseEntity<ApiResponse<List<PublishHistoryItemDto>>> getPublishHistory(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.getPublishHistory(workspaceId, resolveUserId(auth))));
    }

    @Operation(summary = "Get one publish snapshot with full config")
    @GetMapping("/{workspaceId}/storefront/publish-history/{snapshotId}")
    public ResponseEntity<ApiResponse<PublishedStorefrontDto>> getPublishSnapshot(
            @PathVariable UUID workspaceId,
            @PathVariable UUID snapshotId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.getPublishSnapshot(workspaceId, snapshotId, resolveUserId(auth))));
    }

    @Operation(summary = "Unpublish storefront")
    @PostMapping("/{workspaceId}/storefront/unpublish")
    public ResponseEntity<ApiResponse<UnpublishResultDto>> unpublishStorefront(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.unpublishStorefront(workspaceId, resolveUserId(auth))));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
