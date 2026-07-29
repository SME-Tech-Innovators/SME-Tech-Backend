package sme.tech.innovators.sme.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import sme.tech.innovators.sme.config.TestAwsS3Config;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.request.BusinessRegistrationRequest;
import sme.tech.innovators.sme.dto.request.RegistrationRequest;
import sme.tech.innovators.sme.dto.response.RegistrationResponse;
import sme.tech.innovators.sme.exception.EmailAlreadyExistsException;
import sme.tech.innovators.sme.repository.BusinessRepository;
import sme.tech.innovators.sme.repository.UserRepository;
import sme.tech.innovators.sme.repository.VerificationTokenRepository;
import sme.tech.innovators.sme.service.RegistrationService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "app.slug.reserved-keywords=admin,api,app,auth,dashboard,login,logout,register,signup,store,support,www"
})
@ActiveProfiles("test")
@Transactional
@Import(TestAwsS3Config.class)
class RegistrationServiceIntegrationTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @MockBean
    private SesClient sesClient;

    @MockBean
    private S3Client s3Client;

    @MockBean
    private S3Presigner s3Presigner;

    private RegistrationRequest buildRequest(String email, String businessName) {
        BusinessRegistrationRequest business = new BusinessRegistrationRequest();
        business.setEmail(email);
        business.setPassword("SecurePass1!");
        business.setFullName("Test User");
        business.setBusinessName(businessName);

        RegistrationRequest request = new RegistrationRequest();
        request.setBusiness(business);
        return request;
    }

    @Test
    void successfulRegistrationCreatesUserBusinessAndToken() {
        RegistrationResponse response = registrationService.registerUserAndBusiness(
                buildRequest("new@example.com", "New Business"), "127.0.0.1");

        assertNotNull(response.getUserId());
        assertNotNull(response.getBusinessId());
        assertNotNull(response.getPublicLink());
        assertTrue(userRepository.existsByEmailAndIsDeletedFalse("new@example.com"));
        assertTrue(businessRepository.findById(response.getBusinessId()).isPresent());
    }

    @Test
    void duplicateEmailThrowsEmailAlreadyExistsException() {
        registrationService.registerUserAndBusiness(
                buildRequest("dup@example.com", "First Business"), "127.0.0.1");

        assertThrows(EmailAlreadyExistsException.class, () ->
                registrationService.registerUserAndBusiness(
                        buildRequest("dup@example.com", "Second Business"), "127.0.0.2"));
    }
}
