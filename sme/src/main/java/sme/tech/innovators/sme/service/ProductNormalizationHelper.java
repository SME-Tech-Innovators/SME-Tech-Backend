package sme.tech.innovators.sme.service;

import org.springframework.stereotype.Component;
import sme.tech.innovators.sme.exception.InvalidProductDataException;
import sme.tech.innovators.sme.exception.InvalidProductPriceException;

import java.net.URI;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ProductNormalizationHelper {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("ZAR");

    public String normalizeCurrency(String currency) {
        String value = (currency == null || currency.isBlank()) ? "ZAR" : currency.trim().toUpperCase();
        if (!SUPPORTED_CURRENCIES.contains(value)) {
            throw new InvalidProductPriceException(
                    "Unsupported currency '" + value + "'. Supported: " + SUPPORTED_CURRENCIES);
        }
        return value;
    }

    public void validatePriceAmount(Integer priceAmount) {
        if (priceAmount == null) {
            throw new InvalidProductPriceException("priceAmount is required");
        }
        if (priceAmount < 0) {
            throw new InvalidProductPriceException("priceAmount must be >= 0");
        }
    }

    /**
     * Validates optional compare-at price. Null is allowed (not on sale).
     * When set, must be strictly greater than {@code priceAmount}.
     */
    public void validateCompareAtPriceAmount(Integer compareAtPriceAmount, Integer priceAmount) {
        if (compareAtPriceAmount == null) {
            return;
        }
        if (compareAtPriceAmount < 0) {
            throw new InvalidProductPriceException("compareAtPriceAmount must be >= 0");
        }
        if (priceAmount == null) {
            throw new InvalidProductPriceException("priceAmount is required when setting compareAtPriceAmount");
        }
        if (compareAtPriceAmount <= priceAmount) {
            throw new InvalidProductPriceException(
                    "compareAtPriceAmount must be greater than priceAmount");
        }
    }

    /** Derived merchandising flag — not persisted. */
    public boolean isOnSale(Integer compareAtPriceAmount, Integer priceAmount) {
        return compareAtPriceAmount != null
                && priceAmount != null
                && compareAtPriceAmount > priceAmount;
    }

    public String formatPriceLabel(Integer priceAmount, String currency) {
        if (priceAmount == null || currency == null) {
            return null;
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA"));
        try {
            format.setCurrency(Currency.getInstance(currency));
        } catch (IllegalArgumentException ignored) {
            // keep default
        }
        return format.format(priceAmount / 100.0);
    }

    public void validateOptionalUrl(String url, String fieldName) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new InvalidProductDataException(fieldName + " must be a valid http(s) URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidProductDataException(fieldName + " must be a valid URL");
        }
    }

    public void validateGalleryUrls(List<String> galleryUrls) {
        if (galleryUrls == null) {
            return;
        }
        for (int i = 0; i < galleryUrls.size(); i++) {
            validateOptionalUrl(galleryUrls.get(i), "galleryUrls[" + i + "]");
        }
    }
}
