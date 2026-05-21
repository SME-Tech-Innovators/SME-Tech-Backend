package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.entity.AccountStatus;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.VerificationToken;
import sme.tech.innovators.sme.exception.AlreadyVerifiedException;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.exception.TokenExpiredException;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.repository.VerificationTokenRepository;
import sme.tech.innovators.sme.util.TokenGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;
    private final TokenGenerator tokenGenerator;
    private final EmailService emailService;

    @Transactional
    public VerificationToken createVerificationToken(User user) {
        // @Modifying JPQL delete executes immediately, bypassing Hibernate's action queue
        verificationTokenRepository.deleteByUser(user);
        verificationTokenRepository.flush();
        VerificationToken token = VerificationToken.builder()
                .token(tokenGenerator.generateSecureToken())
                .user(user)
                .build();
        return verificationTokenRepository.saveAndFlush(token);
    }

    @Transactional
    public void verifyToken(String tokenValue) {
        VerificationToken token = verificationTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidTokenException(
                        "This verification link is invalid or has already been used. " +
                        "If your account is already verified, you can log in directly."));

        User user = token.getUser();

        if (user.getAccountStatus() == AccountStatus.VERIFIED) {
            verificationTokenRepository.delete(token);
            throw new AlreadyVerifiedException(
                    "Your email address has already been verified. You can log in now.");
        }

        if (token.isExpired()) {
            throw new TokenExpiredException(
                    "This verification link has expired. Verification links are valid for 24 hours. " +
                    "Please request a new verification email.");
        }

        user.setAccountStatus(AccountStatus.VERIFIED);
        userRepository.save(user);
        verificationTokenRepository.delete(token);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        VerificationToken newToken = createVerificationToken(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), newToken.getToken());
    }
}
