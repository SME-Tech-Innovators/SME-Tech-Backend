package sme.tech.innovators.sme.shipping;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShippingQuoteCache {

    private static final long TTL_SECONDS = 30 * 60;

    private final Map<String, CachedQuote> byToken = new ConcurrentHashMap<>();

    public String store(CachedQuote quote) {
        String token = UUID.randomUUID().toString();
        byToken.put(token, quote);
        return token;
    }

    public CachedQuote get(String token, UUID workspaceId) {
        if (token == null || token.isBlank()) {
            return null;
        }
        CachedQuote cached = byToken.get(token);
        if (cached == null) {
            return null;
        }
        if (!cached.workspaceId().equals(workspaceId)) {
            return null;
        }
        if (Instant.now().isAfter(cached.expiresAt())) {
            byToken.remove(token);
            return null;
        }
        return cached;
    }

    public record CachedQuote(
            UUID workspaceId,
            UUID cartId,
            String optionId,
            String provider,
            int amountMinor,
            String currency,
            String label,
            Map<String, Object> bobgoShipmentMeta,
            Map<String, Object> ratesPayload,
            Instant expiresAt
    ) {
        public static CachedQuote create(UUID workspaceId,
                                         UUID cartId,
                                         String optionId,
                                         String provider,
                                         int amountMinor,
                                         String currency,
                                         String label,
                                         Map<String, Object> bobgoShipmentMeta,
                                         Map<String, Object> ratesPayload) {
            return new CachedQuote(
                    workspaceId,
                    cartId,
                    optionId,
                    provider,
                    amountMinor,
                    currency,
                    label,
                    bobgoShipmentMeta,
                    ratesPayload,
                    Instant.now().plusSeconds(TTL_SECONDS)
            );
        }
    }
}
