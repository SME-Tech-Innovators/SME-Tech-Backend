package sme.tech.innovators.sme;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.ses.SesClient;

@SpringBootTest
@ActiveProfiles("test")
class SmeApplicationTests {

	@MockBean
	private SesClient sesClient;

	@Test
	void contextLoads() {
	}

}
