package sme.tech.innovators.sme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BobGoConfig {

    @Value("${app.bobgo.base-url:https://api.sandbox.bobgo.co.za/v2/}")
    private String baseUrl;

    @Value("${app.bobgo.bearer-token:}")
    private String bearerToken;

    @Value("${app.bobgo.sandbox:true}")
    private boolean sandbox;

    @Value("${app.bobgo.webhook-secret:}")
    private String webhookSecret;

    @Bean
    public RestClient bobGoRestClient() {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return RestClient.builder()
                .baseUrl(normalized)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }
}
