package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.DeleteAccountRequest;
import sme.tech.innovators.sme.dto.request.UpdateBusinessRequest;
import sme.tech.innovators.sme.dto.request.UpdatePasswordRequest;
import sme.tech.innovators.sme.dto.request.UpdateProfileRequest;
import sme.tech.innovators.sme.dto.response.AccountProfileDto;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.AccountService;

import java.util.UUID;

@Tag(name = "Account", description = "Account profile management — view profile, update name/password/business, and delete account")
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class AccountController {

    private final AccountService accountService;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // GET /me
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get account profile",
            description = "Returns the authenticated user's profile including user details and linked business information."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User or business not found")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AccountProfileDto>> getProfile(Authentication auth) {
        UUID userId = extractUserId(auth);
        AccountProfileDto dto = accountService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // -------------------------------------------------------------------------
    // PUT /profile
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Update display name",
            description = "Updates the authenticated user's full name. Triggers updatedAt refresh."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error — fullName is blank or too long"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<AccountProfileDto>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        AccountProfileDto dto = accountService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // -------------------------------------------------------------------------
    // PUT /password
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Change password",
            description = "Verifies the current password, validates the new password complexity, updates the hash, and revokes all existing refresh tokens."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error — new password too short or missing complexity"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Current password is incorrect or JWT missing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<String>> updatePassword(
            @Valid @RequestBody UpdatePasswordRequest request,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        accountService.updatePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully"));
    }

    // -------------------------------------------------------------------------
    // PUT /business
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Update business details",
            description = "Updates the business name and/or description. Slug and public link are never changed. EMPLOYEE role is not permitted."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Business updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error — name too short or description too long"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "EMPLOYEE role is not permitted to update business details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User or business not found")
    })
    @PutMapping("/business")
    public ResponseEntity<ApiResponse<AccountProfileDto>> updateBusiness(
            @Valid @RequestBody UpdateBusinessRequest request,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        String role = extractRole(auth);
        AccountProfileDto dto = accountService.updateBusiness(userId, role, request);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // -------------------------------------------------------------------------
    // DELETE /
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Delete account",
            description = "Soft-deletes the authenticated user's account and their business (if no other admin exists). All refresh tokens are revoked. Requires password confirmation."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error — password is blank"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Password is incorrect or JWT missing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping
    public ResponseEntity<ApiResponse<String>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication auth) {
        UUID userId = extractUserId(auth);
        accountService.deleteAccount(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully"));
    }

    /**
     * Resolves the authenticated user's UUID from the Spring Security principal.
     * The principal name is the user's email (set by CustomUserDetailsService),
     * so we look up the user record to obtain the UUID.
     *
     * @throws InvalidTokenException if the principal cannot be resolved to an active user
     */
    private UUID extractUserId(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new InvalidTokenException("Authenticated user not found: " + email));
        return user.getId();
    }

    /**
     * Extracts the role string from the first granted authority.
     * Authorities are stored as "ROLE_OWNER", "ROLE_ADMIN", "ROLE_EMPLOYEE" —
     * this method strips the "ROLE_" prefix and returns the bare role name.
     *
     * @return role name (e.g. "OWNER", "ADMIN", "EMPLOYEE"), or empty string if absent
     */
    private String extractRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .orElse("");
    }
}
