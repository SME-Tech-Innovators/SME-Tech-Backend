package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.entity.PasswordResetToken;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.exception.TokenExpiredException;
import sme.tech.innovators.sme.repository.PasswordResetTokenRepository;
import sme.tech.innovators.sme.repository.RefreshTokenRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.util.TokenGenerator;
import sme.tech.innovators.sme.validator.PasswordValidator;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final AuditService auditService;

    /**
     * Initiates a password reset for the given email.
     * Always returns successfully even if the email is not found —
     * this prevents user enumeration attacks.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        Optional<User> userOpt = userRepository.findByEmailAndIsDeletedFalse(email);
        if (userOpt.isEmpty()) {
            // Do not reveal whether the email exists — log only, no exception
            log.info("Password reset requested for unknown email: {}", email);
            return;
        }

        User user = userOpt.get();

        // Replace any existing reset token for this user
        passwordResetTokenRepository.deleteByUser(user);
        passwordResetTokenRepository.flush();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(tokenGenerator.generateSecureToken())
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        passwordResetTokenRepository.saveAndFlush(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetToken.getToken());
        auditService.logSecurityEvent("PASSWORD_RESET_REQUESTED", "system", email,
                "Password reset token issued");
    }

    /**
     * Validates the reset token and sets the new password.
     * Revokes all existing sessions after the reset.
     */
    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidTokenException(
                        "This password reset link is invalid or has already been used."));

        if (resetToken.isExpired()) {
            throw new TokenExpiredException(
                    "This password reset link has expired. Reset links are valid for 1 hour. " +
                    "Please request a new one.");
        }

        // Validate new password against complexity rules
        passwordValidator.validate(newPassword);

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Consume the token — one-time use only
        passwordResetTokenRepository.delete(resetToken);

        // Revoke all active refresh tokens so old sessions cannot be reused
        refreshTokenRepository.revokeAllByUserId(user.getId(), LocalDateTime.now());

        auditService.logSecurityEvent("PASSWORD_RESET_COMPLETED", "system", user.getEmail(),
                "Password reset successfully; all sessions revoked");
        log.info("Password reset completed for userId={}", user.getId());
    }
}
