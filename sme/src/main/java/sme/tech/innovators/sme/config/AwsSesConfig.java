package sme.tech.innovators.sme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
public class AwsSesConfig {

    @Value("${app.aws.region}")
    private String awsRegion;

    @Value("${app.aws.access-key}")
    String accessKey;

    @Value("${app.aws.secret-key}")
    String secretKey;

    @Bean
    public SesClient sesClient() {

        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: AWS_ACCESS_KEY_ID");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: AWS_SECRET_ACCESS_KEY");
        }
        if (awsRegion == null || awsRegion.isBlank()) {
            throw new IllegalStateException(
                    "Missing required configuration property: app.aws.region (AWS_REGION)");
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return SesClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
