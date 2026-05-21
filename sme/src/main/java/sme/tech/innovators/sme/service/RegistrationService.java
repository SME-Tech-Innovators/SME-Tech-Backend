package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.RegistrationRequest;
import sme.tech.innovators.sme.dto.response.RegistrationResponse;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.entity.User;
import sme.tech.innovators.sme.entity.VerificationToken;
import sme.tech.innovators.sme.exception.EmailAlreadyExistsException;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.validator.PasswordValidator;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final SlugGeneratorService slugGeneratorService;
    private final VerificationService verificationService;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final AuditService auditService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public RegistrationResponse registerUserAndBusiness(RegistrationRequest request, String ipAddress) {
        String email = request.getBusiness().getEmail().trim().toLowerCase();
        String businessName = request.getBusiness().getBusinessName().trim();

        auditService.logRegistrationAttempt(ipAddress, email);

        try {
            rateLimitService.checkRateLimit(ipAddress, email);
            rateLimitService.incrementAttempt(ipAddress, email);
        } catch (Exception e) {
            auditService.logRateLimitViolation(ipAddress, email, "REGISTRATION");
            throw e;
        }

        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            auditService.logRegistrationFailure(email, ipAddress, "EMAIL_EXISTS", null);
            throw new EmailAlreadyExistsException(email);
        }

        passwordValidator.validate(request.getBusiness().getPassword());

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getBusiness().getPassword()))
                .fullName(request.getBusiness().getFullName().trim())
                .build();
        user = userRepository.saveAndFlush(user);

        String slug = slugGeneratorService.generateUniqueSlug(businessName);
        String publicLink = frontendUrl + "/store/" + slug;

        Business business = Business.builder()
                .name(businessName)
                .slug(slug)
                .publicLink(publicLink)
                .description(request.getBusiness().getDescription())
                .owner(user)
                .build();
        businessRepository.save(business);

        VerificationToken token = verificationService.createVerificationToken(user);
        emailService.sendVerificationEmail(email, user.getFullName(), token.getToken());

        rateLimitService.resetAttempts(ipAddress, email);
        auditService.logRegistrationSuccess(user.getId(), business.getId(), email, ipAddress);

        return RegistrationResponse.builder()
                .userId(user.getId())
                .businessId(business.getId())
                .publicLink(publicLink)
                .message("Registration successful. Please check your email to verify your account.")
                .build();
    }
}
