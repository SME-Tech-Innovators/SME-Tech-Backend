package sme.tech.innovators.sme.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Slf4j
@Component
@ConfigurationProperties(prefix = "app.shiplogic")
public class ShipLogicProperties {

    private boolean enabled;
    private String apiKey = "";
    private String mode = "sandbox";
    private int providerId = 0;
    private String baseUrl = "https://api.shiplogic.com";

    @PostConstruct
    public void logConfiguration() {
        log.info("########## SHIPLOGIC CONFIG ##########");
        log.info("enabled={}", enabled);
        log.info("mode={}", mode);
        log.info("baseUrl={}", baseUrl);
        log.info("providerId={}", providerId);
        log.info("apiKeyPresent={}", apiKey != null && !apiKey.isBlank());
        log.info("apiKeyLength={}", apiKey == null ? 0 : apiKey.length());
        log.info("configured={}", isConfigured());
        log.info("######################################");
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}