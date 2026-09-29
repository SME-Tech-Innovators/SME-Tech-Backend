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

    @Value("${app.aws.access-key}")
    private String awsAccessKey;

    @Value("${app.aws.secret-key}")
    private String awsSecretKey;

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
        if (awsAccessKey == null || awsAccessKey.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: app.aws.access-key (AWS_ACCESS_KEY_ID)");
        }
        if (awsSecretKey == null || awsSecretKey.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: app.aws.secret-key (AWS_SECRET_ACCESS_KEY)");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKey, awsSecretKey));
    }

    private String requireRegion() {
        if (awsRegion == null || awsRegion.isBlank()) {
            throw new IllegalStateException("Missing required configuration property: app.aws.region (AWS_REGION)");
        }
        return awsRegion;
    }
}
