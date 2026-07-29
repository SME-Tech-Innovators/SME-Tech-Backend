package sme.tech.innovators.sme.unit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.ses.SesClient;
import sme.tech.innovators.sme.config.SecurityConfig;
import sme.tech.innovators.sme.controller.AuthController;
import sme.tech.innovators.sme.exception.GlobalExceptionHandler;
import sme.tech.innovators.sme.exception.InvalidTokenException;
import sme.tech.innovators.sme.exception.TokenExpiredException;
import sme.tech.innovators.sme.security.CustomUserDetailsService;
import sme.tech.innovators.sme.security.JwtAuthenticationFilter;
import sme.tech.innovators.sme.service.AuthService;
import sme.tech.innovators.sme.service.JwtService;
import sme.tech.innovators.sme.service.RateLimitService;
import sme.tech.innovators.sme.service.RegistrationService;
import sme.tech.innovators.sme.service.VerificationService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:3000"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VerificationService verificationService;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private AuthService authService;

    // Prevents AwsSesConfig from trying to build a real SesClient
    @MockBean
    private SesClient sesClient;

    // Required by SecurityConfig
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtService jwtService;

    // Required by WebMvcConfig → RateLimitInterceptor
    @MockBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() throws Exception {
        // Configure the JWT filter mock to pass through without intercepting requests
        doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/auth/verify — valid token → 302 redirect
    // -------------------------------------------------------------------------

    @Test
    void verify_validToken_returns200WithSuccessBody() throws Exception {
        doNothing().when(verificationService).verifyToken(anyString());

        mockMvc.perform(get("/api/v1/auth/verify").param("token", "valid-token"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/auth/verify — invalid token → 404, no redirect
    // -------------------------------------------------------------------------

    @Test
    void verify_invalidToken_returns404_noRedirect() throws Exception {
        doThrow(new InvalidTokenException("Token not found"))
                .when(verificationService).verifyToken(anyString());

        mockMvc.perform(get("/api/v1/auth/verify").param("token", "bad-token"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/auth/verify — expired token → 400, no redirect
    // -------------------------------------------------------------------------

    @Test
    void verify_expiredToken_returns400_noRedirect() throws Exception {
        doThrow(new TokenExpiredException("Token has expired"))
                .when(verificationService).verifyToken(anyString());

        mockMvc.perform(get("/api/v1/auth/verify").param("token", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));
    }
}
