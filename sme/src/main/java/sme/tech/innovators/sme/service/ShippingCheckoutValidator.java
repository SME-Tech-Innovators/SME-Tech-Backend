package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sme.tech.innovators.sme.dto.request.CheckoutRequest;
import sme.tech.innovators.sme.entity.Cart;
import sme.tech.innovators.sme.entity.WorkspaceShippingSettings;
import sme.tech.innovators.sme.exception.CheckoutValidationException;
import sme.tech.innovators.sme.exception.ShippingInvalidSelectionException;
import sme.tech.innovators.sme.shipping.ShippingMoneyUtils;
import sme.tech.innovators.sme.shipping.ShippingQuoteCache;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ShippingCheckoutValidator {

    private final WorkspaceShippingSettingsService shippingSettingsService;
    private final ShippingQuoteCache quoteCache;

    public ValidatedShipping validate(UUID workspaceId, Cart cart, CheckoutRequest request) {
        Optional<WorkspaceShippingSettings> settingsOpt =
                shippingSettingsService.findSettings(workspaceId);

        if (settingsOpt.isEmpty() || !settingsOpt.get().isEnabled()) {
            return ValidatedShipping.free();
        }

        WorkspaceShippingSettings settings = settingsOpt.get();
        CheckoutRequest.ShippingSelection selection = request.getShippingSelection();
        if (selection == null || selection.getOptionId() == null || selection.getOptionId().isBlank()) {
            throw new CheckoutValidationException(
                    "shippingSelection is required when shipping is enabled for this store");
        }

        String optionId = selection.getOptionId().trim();
        String provider = selection.getProvider() != null ? selection.getProvider().trim() : "manual";
        String currency = selection.getCurrency() != null ? selection.getCurrency() : cart.getCurrency();

        if ("pickup".equals(optionId)) {
            if (!settings.isAllowPickup()) {
                throw new ShippingInvalidSelectionException("Store pickup is not available");
            }
            assertAmount(selection.getAmount(), 0);
            return new ValidatedShipping(
                    BigDecimal.ZERO,
                    "pickup",
                    "manual",
                    optionId,
                    "Store pickup",
                    null,
                    null,
                    toSelectionSnapshot(selection, optionId, provider, 0, currency)
            );
        }

        if ("manual:flat".equals(optionId)) {
            if (settings.getFallbackFlatRateAmount() == null) {
                throw new ShippingInvalidSelectionException("Flat-rate shipping is not configured");
            }
            int expectedMinor = ShippingMoneyUtils.majorToMinor(settings.getFallbackFlatRateAmount());
            assertAmount(selection.getAmount(), expectedMinor);
            BigDecimal major = ShippingMoneyUtils.minorToMajor(expectedMinor);
            return new ValidatedShipping(
                    major,
                    "flat",
                    "manual",
                    optionId,
                    "Standard delivery",
                    null,
                    null,
                    toSelectionSnapshot(selection, optionId, provider, expectedMinor, currency)
            );
        }

        if (optionId.startsWith("bobgo:")) {
            ShippingQuoteCache.CachedQuote cached = quoteCache.get(selection.getBobgoRateToken(), workspaceId);
            if (cached == null || !cached.cartId().equals(cart.getId())) {
                throw new ShippingInvalidSelectionException(
                        "Shipping quote expired or invalid — request a new quote");
            }
            if (!cached.optionId().equals(optionId)) {
                throw new ShippingInvalidSelectionException("Selected shipping option does not match quote");
            }
            assertAmount(selection.getAmount(), cached.amountMinor());
            BigDecimal major = ShippingMoneyUtils.minorToMajor(cached.amountMinor());
            Map<String, Object> snapshot = toSelectionSnapshot(
                    selection, optionId, "bobgo", cached.amountMinor(), cached.currency());
            snapshot.put("bobgoRateToken", selection.getBobgoRateToken());
            if (cached.bobgoShipmentMeta() != null) {
                snapshot.put("bobgoShipmentMeta", cached.bobgoShipmentMeta());
            }
            if (cached.ratesPayload() != null) {
                snapshot.put("bobgoRatesPayload", cached.ratesPayload());
            }
            return new ValidatedShipping(
                    major,
                    cached.label(),
                    "bobgo",
                    optionId,
                    cached.label(),
                    cached.bobgoShipmentMeta(),
                    cached.ratesPayload(),
                    snapshot
            );
        }

        throw new ShippingInvalidSelectionException("Unknown shipping option: " + optionId);
    }

    private static void assertAmount(Integer clientAmount, int expectedMinor) {
        if (clientAmount == null || clientAmount != expectedMinor) {
            throw new ShippingInvalidSelectionException("Shipping amount does not match quoted price");
        }
    }

    private static Map<String, Object> toSelectionSnapshot(CheckoutRequest.ShippingSelection selection,
                                                            String optionId,
                                                            String provider,
                                                            int amountMinor,
                                                            String currency) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("optionId", optionId);
        map.put("provider", provider);
        map.put("amount", amountMinor);
        map.put("currency", currency);
        if (selection.getBobgoRateToken() != null) {
            map.put("bobgoRateToken", selection.getBobgoRateToken());
        }
        return map;
    }

    public record ValidatedShipping(
            BigDecimal shippingAmountMajor,
            String shippingMethod,
            String shippingProvider,
            String shippingOptionId,
            String shippingOptionLabel,
            Map<String, Object> bobgoShipmentMeta,
            Map<String, Object> bobgoRatesPayload,
            Map<String, Object> selectionSnapshot
    ) {
        public static ValidatedShipping free() {
            return new ValidatedShipping(
                    BigDecimal.ZERO, null, null, null, null, null, null, null);
        }
    }
}
