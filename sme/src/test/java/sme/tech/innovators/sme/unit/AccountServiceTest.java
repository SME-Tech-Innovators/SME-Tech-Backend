package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import sme.tech.innovators.sme.dto.request.DeleteAccountRequest;
import sme.tech.innovators.sme.dto.request.UpdateBusinessRequest;
import sme.tech.innovators.sme.dto.request.UpdatePasswordRequest;
import sme.tech.innovators.sme.dto.request.UpdateProfileRequest;
import sme.tech.innovators.sme.dto.response.AccountProfileDto;
import sme.tech.innovators.sme.entity.AccountStatus;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.entity.RefreshToken;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.UserRole;
import sme.tech.innovators.sme.exception.BusinessAccessDeniedException;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.exception.PasswordValidationException;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.repository.RefreshTokenRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.service.AccountService;
import sme.tech.innovators.sme.service.AuditService;
import sme.tech.innovators.sme.validator.PasswordValidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordValidator passwordValidator;
    @Mock private AuditService auditService;

    @InjectMocks
    private AccountService accountService;

    private UUID userId;
    private UUID businessId;
    private User user;
    private Business business;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        businessId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("owner@example.com")
                .password("$2a$12$hashedPassword")
                .fullName("Jane Doe")
                .accountStatus(AccountStatus.VERIFIED)
                .role(UserRole.OWNER)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        business = Business.builder()
                .id(businessId)
                .name("Acme Coffee")
                .slug("acme-coffee")
                .publicLink("https://example.com/store/acme-coffee")
                .description("Best coffee in town")
                .owner(user)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    // -------------------------------------------------------------------------
    // getProfile
    // -------------------------------------------------------------------------

    @Test
    void getProfile_returnsCorrectAccountProfileDto() {
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));

        AccountProfileDto dto = accountService.getProfile(userId);

        assertNotNull(dto);
        assertEquals(userId, dto.getUserId());
        assertEquals("owner@example.com", dto.getEmail());
        assertEquals("Jane Doe", dto.getFullName());
        assertEquals(AccountStatus.VERIFIED, dto.getAccountStatus());
        assertEquals(UserRole.OWNER, dto.getRole());
        assertEquals(businessId, dto.getBusinessId());
        assertEquals("Acme Coffee", dto.getBusinessName());
        assertEquals("acme-coffee", dto.getSlug());
        assertEquals("https://example.com/store/acme-coffee", dto.getPublicLink());
        assertEquals("Best coffee in town", dto.getBusinessDescription());
    }

    @Test
    void getProfile_throwsInvalidTokenException_whenUserNotFound() {
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> accountService.getProfile(userId));
    }

    @Test
    void getProfile_throwsInvalidTokenException_whenBusinessNotFound() {
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> accountService.getProfile(userId));
    }

    // -------------------------------------------------------------------------
    // updateProfile
    // -------------------------------------------------------------------------

    @Test
    void updateProfile_trimsNameAndSaves() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("  New Name  ");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));

        AccountProfileDto dto = accountService.updateProfile(userId, request);

        assertEquals("New Name", dto.getFullName());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("New Name", captor.getValue().getFullName());
    }

    @Test
    void updateProfile_logsAuditEvent() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));

        accountService.updateProfile(userId, request);

        verify(auditService).logSecurityEvent(eq("PROFILE_UPDATE"), anyString(), eq("owner@example.com"), anyString());
    }

    @Test
    void updateProfile_returnsUpdatedDto() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Alice Smith");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));

        AccountProfileDto dto = accountService.updateProfile(userId, request);

        assertEquals("Alice Smith", dto.getFullName());
        assertEquals(businessId, dto.getBusinessId());
    }

    // -------------------------------------------------------------------------
    // updatePassword
    // -------------------------------------------------------------------------

    @Test
    void updatePassword_throwsBadCredentials_whenCurrentPasswordWrong() {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("wrongPassword");
        request.setNewPassword("NewSecure1!");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", user.getPassword())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> accountService.updatePassword(userId, request));
        verify(passwordValidator, never()).validate(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updatePassword_throwsPasswordValidationException_whenNewPasswordWeak() {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("correctPassword");
        request.setNewPassword("weak");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPassword())).thenReturn(true);
        doThrow(new PasswordValidationException(List.of("Password must be at least 8 characters long")))
                .when(passwordValidator).validate("weak");

        assertThrows(PasswordValidationException.class, () -> accountService.updatePassword(userId, request));
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).findAllByUserId(any());
    }

    @Test
    void updatePassword_encodesNewPasswordAndRevokesTokensOnSuccess() {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("correctPassword");
        request.setNewPassword("NewSecure1!");

        RefreshToken token1 = RefreshToken.builder().id(UUID.randomUUID()).userId(userId).revoked(false).build();
        RefreshToken token2 = RefreshToken.builder().id(UUID.randomUUID()).userId(userId).revoked(false).build();

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPassword())).thenReturn(true);
        doNothing().when(passwordValidator).validate("NewSecure1!");
        when(passwordEncoder.encode("NewSecure1!")).thenReturn("$2a$12$newHashedPassword");
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of(token1, token2));
        when(refreshTokenRepository.saveAll(any())).thenReturn(List.of(token1, token2));

        assertDoesNotThrow(() -> accountService.updatePassword(userId, request));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("$2a$12$newHashedPassword", userCaptor.getValue().getPassword());

        // Both tokens should be revoked
        assertTrue(token1.isRevoked());
        assertTrue(token2.isRevoked());
        verify(refreshTokenRepository).saveAll(anyList());
    }

    @Test
    void updatePassword_logsAuditEvent_onSuccess() {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("correctPassword");
        request.setNewPassword("NewSecure1!");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPassword())).thenReturn(true);
        doNothing().when(passwordValidator).validate(any());
        when(passwordEncoder.encode(any())).thenReturn("$2a$12$newHash");
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(refreshTokenRepository.saveAll(any())).thenReturn(List.of());

        accountService.updatePassword(userId, request);

        verify(auditService).logSecurityEvent(eq("PASSWORD_CHANGE"), anyString(), eq("owner@example.com"), anyString());
    }

    // -------------------------------------------------------------------------
    // updateBusiness
    // -------------------------------------------------------------------------

    @Test
    void updateBusiness_throwsBusinessAccessDeniedException_forEmployeeRole() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("New Name");

        assertThrows(BusinessAccessDeniedException.class,
                () -> accountService.updateBusiness(userId, "EMPLOYEE", request));

        verify(userRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void updateBusiness_throwsBusinessAccessDeniedException_forEmployeeRoleCaseInsensitive() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("New Name");

        assertThrows(BusinessAccessDeniedException.class,
                () -> accountService.updateBusiness(userId, "employee", request));
    }

    @Test
    void updateBusiness_updatesNameWithoutChangingSlug() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("New Business Name");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));
        when(businessRepository.save(any())).thenReturn(business);

        AccountProfileDto dto = accountService.updateBusiness(userId, "OWNER", request);

        assertEquals("New Business Name", dto.getBusinessName());
        // Slug must remain unchanged
        assertEquals("acme-coffee", dto.getSlug());
        assertEquals("https://example.com/store/acme-coffee", dto.getPublicLink());
    }

    @Test
    void updateBusiness_trimsNameBeforeSaving() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("  Trimmed Name  ");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));
        when(businessRepository.save(any())).thenReturn(business);

        accountService.updateBusiness(userId, "OWNER", request);

        ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(captor.capture());
        assertEquals("Trimmed Name", captor.getValue().getName());
    }

    @Test
    void updateBusiness_throwsIllegalArgument_whenNameTooShort() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("A"); // 1 char — below minimum of 2

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));

        assertThrows(IllegalArgumentException.class,
                () -> accountService.updateBusiness(userId, "OWNER", request));
    }

    @Test
    void updateBusiness_sanitizesDescriptionHtml() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setDescription("<script>alert('xss')</script><b>Bold</b> plain text");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));
        when(businessRepository.save(any())).thenReturn(business);

        accountService.updateBusiness(userId, "OWNER", request);

        ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(captor.capture());
        String savedDescription = captor.getValue().getDescription();

        assertFalse(savedDescription.contains("<script>"), "Script tags must be removed");
        assertFalse(savedDescription.contains("alert("), "Script content must be removed");
        assertFalse(savedDescription.contains("<b>"), "HTML tags must be stripped");
        assertTrue(savedDescription.contains("plain text"), "Plain text content must be preserved");
    }

    @Test
    void updateBusiness_doesNotChangeNameWhenNameIsNull() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName(null);
        request.setDescription("Updated description");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));
        when(businessRepository.save(any())).thenReturn(business);

        accountService.updateBusiness(userId, "OWNER", request);

        ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(captor.capture());
        // Name should remain unchanged
        assertEquals("Acme Coffee", captor.getValue().getName());
    }

    @Test
    void updateBusiness_logsAuditEvent() {
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("Updated Name");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));
        when(businessRepository.save(any())).thenReturn(business);

        accountService.updateBusiness(userId, "OWNER", request);

        verify(auditService).logSecurityEvent(eq("BUSINESS_UPDATE"), anyString(), eq("owner@example.com"), anyString());
    }

    // -------------------------------------------------------------------------
    // deleteAccount
    // -------------------------------------------------------------------------

    @Test
    void deleteAccount_throwsBadCredentials_whenPasswordWrong() {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword("wrongPassword");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", user.getPassword())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> accountService.deleteAccount(userId, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAccount_softDeletesUser_onSuccess() {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword("correctPassword");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPassword())).thenReturn(true);
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(refreshTokenRepository.saveAll(any())).thenReturn(List.of());
        when(userRepository.save(any())).thenReturn(user);
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.empty());

        accountService.deleteAccount(userId, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleted());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void deleteAccount_softDeletesBusiness_whenBusinessExists() {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword("correctPassword");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPassword())).thenReturn(true);
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(refreshTokenRepository.saveAll(any())).thenReturn(List.of());
        when(userRepository.save(any())).thenReturn(user);
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));
        when(businessRepository.save(any())).thenReturn(business);

        accountService.deleteAccount(userId, request);

        ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeleted());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void deleteAccount_revokesAllRefreshTokens() {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword("correctPassword");

        RefreshToken token1 = RefreshToken.builder().id(UUID.randomUUID()).userId(userId).revoked(false).build();
        RefreshToken token2 = RefreshToken.builder().id(UUID.randomUUID()).userId(userId).revoked(false).build();

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPassword())).thenReturn(true);
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of(token1, token2));
        when(refreshTokenRepository.saveAll(any())).thenReturn(List.of(token1, token2));
        when(userRepository.save(any())).thenReturn(user);
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.empty());

        accountService.deleteAccount(userId, request);

        assertTrue(token1.isRevoked());
        assertTrue(token2.isRevoked());
        verify(refreshTokenRepository).saveAll(anyList());
    }

    @Test
    void deleteAccount_logsAccountDeletedAuditEvent() {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword("correctPassword");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPassword())).thenReturn(true);
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(refreshTokenRepository.saveAll(any())).thenReturn(List.of());
        when(userRepository.save(any())).thenReturn(user);
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.empty());

        accountService.deleteAccount(userId, request);

        verify(auditService).logSecurityEvent(eq("ACCOUNT_DELETED"), anyString(), eq("owner@example.com"), anyString());
    }

    @Test
    void deleteAccount_logsBusinessSoftDeletedAuditEvent_whenBusinessExists() {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword("correctPassword");

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPassword())).thenReturn(true);
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(refreshTokenRepository.saveAll(any())).thenReturn(List.of());
        when(userRepository.save(any())).thenReturn(user);
        when(businessRepository.findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(user)).thenReturn(Optional.of(business));
        when(businessRepository.save(any())).thenReturn(business);

        accountService.deleteAccount(userId, request);

        verify(auditService).logSecurityEvent(eq("BUSINESS_SOFT_DELETED"), anyString(), eq("owner@example.com"), anyString());
    }
}
