package sme.tech.innovators.sme.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Uber Direct (Uber Eats on-demand courier) platform-level configuration.
 *
 * <p>Credentials are platform-wide — a single OAuth2 client-credentials flow
 * authenticates the platform.  Per-workspace enabling and pickup address are
 * stored in {@link sme.tech.innovators.sme.entity.WorkspaceDeliverySettings}.
 *
 * <p>Sandbox base URL:  https://sandbox-api.uber.com
 * <p>Production base URL: https://api.uber.com
 */
@Getter
@Setter
@Slf4j
@Component
@ConfigurationProperties(prefix = "app.uber-direct")
public class UberDirectProperties {

    /** Toggle the feature.  Must be {@code true} for any call to proceed. */
    private boolean enabled = false;

    /**
     * OAuth2 client-id obtained from the Uber Developer Dashboard.
     * Corresponds to the "Client ID" field in your app's credentials.
     */
    private String clientId = "";

    /**
     * OAuth2 client-secret obtained from the Uber Developer Dashboard.
     */
    private String clientSecret = "";

    /**
     * Your Uber Direct customer-id (also called "customer_id" in the API).
     * Found in the Uber Direct dashboard → Settings → Account.
     */
    private String customerId = "";

    /**
     * {@code sandbox} or {@code production}.
     */
    private String mode = "sandbox";

    /**
     * Base URL for the Uber Direct REST API.
     * Override to switch between sandbox and production.
     */
    private String baseUrl = "https://sandbox-api.uber.com";

    /**
     * OAuth2 token endpoint (same host for sandbox and production).
     */
    private String tokenUrl = "https://auth.uber.com/oauth/v2/token";

    /**
     * When {@code true}, all Uber Direct API calls are skipped and realistic
     * mock responses are returned instead.  Use this for demos, local dev
     * without a verified Uber account, or academic/school projects where a
     * live Uber account is not available.
     *
     * <p>Set {@code app.uber-direct.mock-mode=true} in
     * {@code application-local.yaml} or via {@code UBER_DIRECT_MOCK_MODE=true}.
     * The full integration code path still runs — only the HTTP call to Uber
     * is replaced with a canned response.
     */
    private boolean mockMode = false;

    @PostConstruct
    public void logConfiguration() {
        log.info("########## UBER DIRECT CONFIG ##########");
        log.info("enabled={}", enabled);
        log.info("mode={}", mode);
        log.info("baseUrl={}", baseUrl);
        log.info("customerId={}", customerId);
        log.info("clientIdPresent={}", clientId != null && !clientId.isBlank());
        log.info("clientSecretPresent={}", clientSecret != null && !clientSecret.isBlank());
        log.info("configured={}", isConfigured());
        log.info("mockMode={}", mockMode);
        log.info("########################################");
    }

    /**
     * Returns {@code true} when all required credentials are present.
     */
    public boolean isConfigured() {
        return enabled
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && customerId != null && !customerId.isBlank();
    }
}
