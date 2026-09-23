package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.integration.bobgo.BobGoTrackingUrls;

import static org.assertj.core.api.Assertions.assertThat;

class BobGoTrackingUrlsTest {

    @Test
    void sandboxTrackingUrlUsesBobGoPublicTrackHost() {
        assertThat(BobGoTrackingUrls.publicTrackingUrl("UASSV9JN", true))
                .isEqualTo("https://track.sandbox.bobgo.co.za/UASSV9JN");
    }

    @Test
    void productionTrackingUrlUsesProductionHost() {
        assertThat(BobGoTrackingUrls.publicTrackingUrl("UASSV9JN", false))
                .isEqualTo("https://track.bobgo.co.za/UASSV9JN");
    }
}
