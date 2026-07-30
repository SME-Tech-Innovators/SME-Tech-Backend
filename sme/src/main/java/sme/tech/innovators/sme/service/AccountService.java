package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.DeleteAccountRequest;
import sme.tech.innovators.sme.dto.request.UpdateBusinessRequest;
import sme.tech.innovators.sme.dto.request.UpdatePasswordRequest;
import sme.tech.innovators.sme.dto.request.UpdateProfileRequest;
import sme.tech.innovators.sme.dto.response.AccountProfileDto;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.entity.RefreshToken;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.BusinessAccessDeniedException;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.repository.RefreshTokenRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.validator.PasswordValidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final AuditService auditService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the full profile (user + business) for the given user.
     */
    @Transactional(readOnly = true)
    public AccountProfileDto getProfile(UUID userId) {
        User user = loadActiveUser(userId);
        Business business = loadBusinessForUser(user);
        return toProfileDto(user, business);
    }

    /**
     * Updates the authenticated user's display name.
     * Triggers @PreUpdate so updatedAt is refreshed automatically.
     */
    @Transactional
    public AccountProfileDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = loadActiveUser(userId);

        user.setFullName(request.getFullName().trim());
        userRepository.save(user);

        auditService.logSecurityEvent("PROFILE_UPDATE", "unknown", user.getEmail(),
                "userId=" + userId);

        Business business = loadBusinessForUser(user);
        return toProfileDto(user, business);
    }

    /**
     * Changes the user's password.
     * Verifies the current password, validates the new one, then revokes all
     * existing refresh tokens so the user must re-authenticate on other devices.
     */
    @Transactional
    public void updatePassword(UUID userId, UpdatePasswordRequest request) {
        User user = loadActiveUser(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        passwordValidator.validate(request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        revokeAllRefreshTokens(userId);

        auditService.logSecurityEvent("PASSWORD_CHANGE", "unknown", user.getEmail(),
                "userId=" + userId);
    }

    /**
     * Updates the business name and/or description for the authenticated user.
     * EMPLOYEE role is not permitted to make changes.
     * Slug and publicLink are never modified here.
     */
    @Transactional
    public AccountProfileDto updateBusiness(UUID userId, String role, UpdateBusinessRequest request) {
        if ("EMPLOYEE".equalsIgnoreCase(role)) {
            throw new BusinessAccessDeniedException("Employees are not permitted to update business details");
        }

        User user = loadActiveUser(userId);
        Business business = loadBusinessForUser(user);

        if (request.getName() != null) {
            String trimmedName = request.getName().trim();
            if (trimmedName.length() < 2) {
                throw new IllegalArgumentException("Business name must be at least 2 characters");
            }
            business.setName(trimmedName);
        }

        if (request.getDescription() != null) {
            business.setDescription(sanitizeDescription(request.getDescription()));
        }

        businessRepository.save(business);

        auditService.logSecurityEvent("BUSINESS_UPDATE", "unknown", user.getEmail(),
                "userId=" + userId + ", businessId=" + business.getId());

        return toProfileDto(user, business);
    }

    /**
     * Soft-deletes the authenticated user's account (and their business if no
     * other admin exists). All refresh tokens are revoked first.
     */
    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = loadActiveUser(userId);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Password is incorrect");
        }

        revokeAllRefreshTokens(userId);

        // Soft-delete the user
        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.logSecurityEvent("ACCOUNT_DELETED", "unknown", user.getEmail(),
                "userId=" + userId);

        // Soft-delete the business if it exists and no other admin can take over
        businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user).ifPresent(business -> {
            business.setDeleted(true);
            business.setDeletedAt(LocalDateTime.now());
            businessRepository.save(business);

            auditService.logSecurityEvent("BUSINESS_SOFT_DELETED", "unknown", user.getEmail(),
                    "userId=" + userId + ", businessId=" + business.getId());
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads a non-deleted user by ID, throwing InvalidTokenException if absent.
     */
    private User loadActiveUser(UUID userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found or account has been deleted"));
    }

    /**
     * Loads the active business owned by the given user, throwing
     * InvalidTokenException if no active business is found.
     */
    private Business loadBusinessForUser(User user) {
        return businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)
                .orElseThrow(() -> new InvalidTokenException("No active business found for this account"));
    }

    /**
     * Marks all refresh tokens for the given user as revoked.
     */
    private void revokeAllRefreshTokens(UUID userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(userId);
        tokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }

    /**
     * Strips {@code <script>} blocks and all remaining HTML tags from the input.
     * Returns null if the input is null.
     */
    private String sanitizeDescription(String input) {
        if (input == null) {
            return null;
        }
        // Remove <script>...</script> blocks (case-insensitive, including newlines)
        String sanitized = input.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        // Strip all remaining HTML tags
        sanitized = sanitized.replaceAll("<[^>]+>", "");
        return sanitized;
    }

    /**
     * Maps a User + Business pair to the AccountProfileDto response object.
     */
    private AccountProfileDto toProfileDto(User user, Business business) {
        return AccountProfileDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .accountStatus(user.getAccountStatus())
                .role(user.getRole())
                .userCreatedAt(user.getCreatedAt())
                .userUpdatedAt(user.getUpdatedAt())
                .businessId(business.getId())
                .businessName(business.getName())
                .businessDescription(business.getDescription())
                .slug(business.getSlug())
                .publicLink(business.getPublicLink())
                .businessUpdatedAt(business.getUpdatedAt())
                .build();
    }
}
