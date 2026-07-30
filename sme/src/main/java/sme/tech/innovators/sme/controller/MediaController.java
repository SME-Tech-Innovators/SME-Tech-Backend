package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.ConfirmMediaUploadRequest;
import sme.tech.innovators.sme.dto.request.CreateUploadUrlRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.MediaAssetDto;
import sme.tech.innovators.sme.dto.response.PageResponse;
import sme.tech.innovators.sme.dto.response.UploadUrlDto;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.MediaService;

import java.util.UUID;

@Tag(name = "Media", description = "Workspace media library and signed S3 uploads")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/media")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class MediaController {

    private final MediaService mediaService;
    private final UserRepository userRepository;

    @Operation(summary = "Create signed upload URL")
    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlDto>> createUploadUrl(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateUploadUrlRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaService.createUploadUrl(workspaceId, resolveUserId(auth), request)));
    }

    @Operation(summary = "Confirm uploaded media")
    @PostMapping("/{mediaId}/confirm")
    public ResponseEntity<ApiResponse<MediaAssetDto>> confirmUpload(
            @PathVariable UUID workspaceId,
            @PathVariable UUID mediaId,
            @RequestBody(required = false) ConfirmMediaUploadRequest request,
            Authentication auth) {
        ConfirmMediaUploadRequest body = request != null ? request : new ConfirmMediaUploadRequest();
        return ResponseEntity.ok(ApiResponse.success(
                mediaService.confirmUpload(workspaceId, mediaId, resolveUserId(auth), body)));
    }

    @Operation(summary = "List media library")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MediaAssetDto>>> listMedia(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int limit,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaService.listMedia(workspaceId, resolveUserId(auth), type, page, limit)));
    }

    @Operation(summary = "Get media asset")
    @GetMapping("/{mediaId}")
    public ResponseEntity<ApiResponse<MediaAssetDto>> getMedia(
            @PathVariable UUID workspaceId,
            @PathVariable UUID mediaId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaService.getMedia(workspaceId, mediaId, resolveUserId(auth))));
    }

    @Operation(summary = "Delete media (soft delete)")
    @DeleteMapping("/{mediaId}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable UUID workspaceId,
            @PathVariable UUID mediaId,
            Authentication auth) {
        mediaService.deleteMedia(workspaceId, mediaId, resolveUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private UUID resolveUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new WorkspaceNotFoundException("Authenticated user not found: " + email));
        return user.getId();
    }
}
