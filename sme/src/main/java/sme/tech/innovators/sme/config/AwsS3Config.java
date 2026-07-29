package sme.tech.innovators.sme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@Profile("!test")
public class AwsS3Config {

    @Value("${app.aws.region}")
    private String awsRegion;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(requireRegion()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(requireRegion()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    private StaticCredentialsProvider credentialsProvider() {
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: AWS_ACCESS_KEY_ID");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: AWS_SECRET_ACCESS_KEY");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private String requireRegion() {
        if (awsRegion == null || awsRegion.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: app.aws.region (AWS_REGION)");
        }
        return awsRegion;
    }
}
