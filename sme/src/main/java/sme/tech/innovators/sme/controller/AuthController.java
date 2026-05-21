package sme.tech.innovators.sme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sme.tech.innovators.sme.dto.request.LoginRequest;
import sme.tech.innovators.sme.dto.request.RefreshTokenRequest;
import sme.tech.innovators.sme.dto.request.RegistrationRequest;
import sme.tech.innovators.sme.dto.response.ApiResponse;
import sme.tech.innovators.sme.dto.response.AuthResponse;
import sme.tech.innovators.sme.dto.response.RegistrationResponse;
import sme.tech.innovators.sme.service.AuthService;
import sme.tech.innovators.sme.service.RegistrationService;
import sme.tech.innovators.sme.service.VerificationService;

import java.net.URI;

@Tag(name = "Authentication", description = "User registration, email verification, login, token refresh and logout")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;
    private final VerificationService verificationService;
    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Operation(summary = "Register a new user and business",
               description = "Creates a user account and business profile. Sends a verification email. Rate limited to 5/hour per IP and 3/hour per email.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registration successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);
        RegistrationResponse response = registrationService.registerUserAndBusiness(request, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Verify email address",
               description = "Validates the token sent to the user's email and activates the account.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirect to frontend on successful verification"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Token expired"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Token not found")
    })
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam String token) {
        verificationService.verifyToken(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl))
                .build();
    }

    @Operation(summary = "Resend verification email",
               description = "Invalidates the old token and sends a new verification email.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification email resent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<String>> resendVerification(@RequestParam String email) {
        verificationService.resendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponse.success("Verification email resent"));
    }

    @Operation(summary = "Login",
               description = "Authenticates a verified user and returns a JWT access token (15 min) and refresh token (7 days).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Email not verified"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Refresh access token",
               description = "Exchanges a valid refresh token for a new JWT access token.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New access token issued"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token expired or revoked")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Logout",
               description = "Revokes the refresh token. Subsequent refresh attempts with this token will return 401.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Token not found")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
