package sme.tech.innovators.sme;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import sme.tech.innovators.sme.config.TestAwsS3Config;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestAwsS3Config.class)
class SmeApplicationTests {

	@MockBean
	private SesClient sesClient;

	@MockBean
	private S3Client s3Client;

	@MockBean
	private S3Presigner s3Presigner;

	@Test
	void contextLoads() {
	}

}
