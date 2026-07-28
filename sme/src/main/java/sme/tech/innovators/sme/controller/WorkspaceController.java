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
import sme.tech.innovators.sme.dto.request.ResetStorefrontDraftRequest;
import sme.tech.innovators.sme.dto.request.UpdateStorefrontDraftRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.StorefrontDraftDto;
import sme.tech.innovators.sme.dto.response.WorkspaceDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.WorkspaceService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Workspaces", description = "Workspace and storefront draft management for authenticated merchants")
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // GET /workspaces
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List workspaces",
            description = "Returns all workspaces owned by the authenticated merchant. Auto-creates a workspace if none exists yet."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Workspaces returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active business found for this account")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceDto>>> listWorkspaces(Authentication auth) {
        UUID userId = resolveUserId(auth);
        List<WorkspaceDto> workspaces = workspaceService.getWorkspacesForUser(userId);
        return ResponseEntity.ok(ApiResponse.success(workspaces));
    }

    // -------------------------------------------------------------------------
    // GET /workspaces/{workspaceId}
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get workspace",
            description = "Returns a single workspace by ID. The authenticated user must own it."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Workspace returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace not found")
    })
    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceDto>> getWorkspace(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        UUID userId = resolveUserId(auth);
        WorkspaceDto workspace = workspaceService.getWorkspace(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    // -------------------------------------------------------------------------
    // GET /workspaces/{workspaceId}/storefront/draft
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get storefront draft",
            description = "Returns the current draft storefront config. Auto-creates the storefront from classic-boutique if it doesn't exist yet."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Draft returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace not found")
    })
    @GetMapping("/{workspaceId}/storefront/draft")
    public ResponseEntity<ApiResponse<StorefrontDraftDto>> getStorefrontDraft(
            @PathVariable UUID workspaceId,
            Authentication auth) {
        UUID userId = resolveUserId(auth);
        StorefrontDraftDto draft = workspaceService.getStorefrontDraft(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.success(draft));
    }

    // -------------------------------------------------------------------------
    // PUT /workspaces/{workspaceId}/storefront/draft
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Update storefront draft",
            description = "Replaces the full draft config. Validates template, version, theme, sections, and pages before saving."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Draft updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid storefront config"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace or template not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Template is disabled")
    })
    @PutMapping("/{workspaceId}/storefront/draft")
    public ResponseEntity<ApiResponse<StorefrontDraftDto>> updateStorefrontDraft(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateStorefrontDraftRequest request,
            Authentication auth) {
        UUID userId = resolveUserId(auth);
        StorefrontDraftDto draft = workspaceService.updateStorefrontDraft(workspaceId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(draft));
    }

    // -------------------------------------------------------------------------
    // POST /workspaces/{workspaceId}/storefront/draft/reset
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Reset storefront draft",
            description = "Resets the draft config back to the template's default config for the specified template and version."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Draft reset to template default"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace or template not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Template is disabled")
    })
    @PostMapping("/{workspaceId}/storefront/draft/reset")
    public ResponseEntity<ApiResponse<StorefrontDraftDto>> resetStorefrontDraft(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody ResetStorefrontDraftRequest request,
            Authentication auth) {
        UUID userId = resolveUserId(auth);
        StorefrontDraftDto draft = workspaceService.resetStorefrontDraft(workspaceId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(draft));
    }

    // -------------------------------------------------------------------------
    // Private helper
    // -------------------------------------------------------------------------

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
