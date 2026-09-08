package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import sme.tech.innovators.sme.entity.AccountStatus;
import sme.tech.innovators.sme.entity.PasswordResetToken;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.UserRole;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.exception.PasswordValidationException;
import sme.tech.innovators.sme.exception.TokenExpiredException;
import sme.tech.innovators.sme.repository.PasswordResetTokenRepository;
import sme.tech.innovators.sme.repository.RefreshTokenRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.AuditService;
import sme.tech.innovators.sme.service.EmailService;
import sme.tech.innovators.sme.service.PasswordResetService;
import sme.tech.innovators.sme.util.TokenGenerator;
import sme.tech.innovators.sme.validator.PasswordValidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenGenerator tokenGenerator;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordValidator passwordValidator;
    @Mock private AuditService auditService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("merchant@example.com")
                .password("$2a$12$hashedPassword")
                .fullName("Jane Doe")
                .accountStatus(AccountStatus.VERIFIED)
                .role(UserRole.OWNER)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    // ── requestPasswordReset ──────────────────────────────────────────────────

    @Test
    void requestPasswordReset_sendsEmailAndCreatesToken_whenUserExists() {
        when(userRepository.findByEmailAndIsDeletedFalse("merchant@example.com"))
                .thenReturn(Optional.of(user));
        when(tokenGenerator.generateSecureToken()).thenReturn("secure-reset-token");

        PasswordResetToken savedToken = PasswordResetToken.builder()
                .token("secure-reset-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(passwordResetTokenRepository.saveAndFlush(any())).thenReturn(savedToken);

        passwordResetService.requestPasswordReset("merchant@example.com");

        verify(passwordResetTokenRepository).deleteByUser(user);
        verify(passwordResetTokenRepository).flush();
        verify(passwordResetTokenRepository).saveAndFlush(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(
                eq("merchant@example.com"), eq("Jane Doe"), eq("secure-reset-token"));
        verify(auditService).logSecurityEvent(
                eq("PASSWORD_RESET_REQUESTED"), anyString(), anyString(), anyString());
    }

    @Test
    void requestPasswordReset_doesNothing_whenEmailNotFound() {
        // Never reveals whether the email is registered (anti-enumeration)
        when(userRepository.findByEmailAndIsDeletedFalse("unknown@example.com"))
                .thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset("unknown@example.com");

        verify(passwordResetTokenRepository, never()).saveAndFlush(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void requestPasswordReset_replacesExistingToken_whenCalledTwice() {
        when(userRepository.findByEmailAndIsDeletedFalse("merchant@example.com"))
                .thenReturn(Optional.of(user));
        when(tokenGenerator.generateSecureToken()).thenReturn("new-token");
        when(passwordResetTokenRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        passwordResetService.requestPasswordReset("merchant@example.com");

        // Old token must be deleted before new one is saved
        verify(passwordResetTokenRepository).deleteByUser(user);
        verify(passwordResetTokenRepository).flush();
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_updatesPasswordAndRevokesTokens_whenValidToken() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        when(passwordResetTokenRepository.findByToken("valid-token"))
                .thenReturn(Optional.of(token));
        doNothing().when(passwordValidator).validate("NewSecure1!");
        when(passwordEncoder.encode("NewSecure1!")).thenReturn("$2a$12$newHash");
        when(userRepository.save(any())).thenReturn(user);

        passwordResetService.resetPassword("valid-token", "NewSecure1!");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("$2a$12$newHash", userCaptor.getValue().getPassword());

        verify(passwordResetTokenRepository).delete(token);
        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any(LocalDateTime.class));
        verify(auditService).logSecurityEvent(
                eq("PASSWORD_RESET_COMPLETED"), anyString(), anyString(), anyString());
    }

    @Test
    void resetPassword_throwsInvalidTokenException_whenTokenNotFound() {
        when(passwordResetTokenRepository.findByToken("nonexistent"))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> passwordResetService.resetPassword("nonexistent", "NewSecure1!"));

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void resetPassword_throwsTokenExpiredException_whenTokenIsExpired() {
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .token("expired-token")
                .user(user)
                .expiresAt(LocalDateTime.now().minusHours(2))
                .build();

        when(passwordResetTokenRepository.findByToken("expired-token"))
                .thenReturn(Optional.of(expiredToken));

        assertThrows(TokenExpiredException.class,
                () -> passwordResetService.resetPassword("expired-token", "NewSecure1!"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_throwsPasswordValidationException_whenPasswordTooWeak() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        when(passwordResetTokenRepository.findByToken("valid-token"))
                .thenReturn(Optional.of(token));
        doThrow(new PasswordValidationException(List.of("Password must be at least 8 characters long")))
                .when(passwordValidator).validate("weak");

        assertThrows(PasswordValidationException.class,
                () -> passwordResetService.resetPassword("valid-token", "weak"));

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void resetPassword_isOneTimeUse_tokenDeletedAfterSuccess() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("one-time-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        when(passwordResetTokenRepository.findByToken("one-time-token"))
                .thenReturn(Optional.of(token));
        doNothing().when(passwordValidator).validate(any());
        when(passwordEncoder.encode(any())).thenReturn("$2a$12$hash");
        when(userRepository.save(any())).thenReturn(user);

        passwordResetService.resetPassword("one-time-token", "ValidPass1!");

        // Token must be consumed — deleted after use
        verify(passwordResetTokenRepository).delete(token);
    }
}
