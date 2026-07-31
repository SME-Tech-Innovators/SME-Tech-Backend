package sme.tech.innovators.sme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PaystackConfig {

    @Value("${app.paystack.secret-key:}")
    private String secretKey;

    @Value("${app.paystack.public-key:}")
    private String publicKey;

    @Value("${app.paystack.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.paystack.base-url:https://api.paystack.co}")
    private String baseUrl;

    @Value("${app.paystack.platform-fee-percent:0}")
    private int platformFeePercent;

    @Bean
    public RestClient paystackRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultHeader("Authorization", "Bearer " + (secretKey == null ? "" : secretKey))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public int getPlatformFeePercent() {
        return platformFeePercent;
    }
}
