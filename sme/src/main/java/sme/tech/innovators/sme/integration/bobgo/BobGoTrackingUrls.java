package sme.tech.innovators.sme.integration.bobgo;

/** Public Bob Go branded tracking pages (sandbox vs production). */
public final class BobGoTrackingUrls {

    private static final String SANDBOX_BASE = "https://track.sandbox.bobgo.co.za";
    private static final String PRODUCTION_BASE = "https://track.bobgo.co.za";

    private BobGoTrackingUrls() {}

    public static String publicTrackingUrl(String trackingReference, boolean sandbox) {
        if (trackingReference == null || trackingReference.isBlank()) {
            return null;
        }
        String base = sandbox ? SANDBOX_BASE : PRODUCTION_BASE;
        return base + "/" + trackingReference.trim();
    }
}
