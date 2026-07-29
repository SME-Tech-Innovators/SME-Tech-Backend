package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.exception.InvalidProductDataException;
import sme.tech.innovators.sme.exception.InvalidProductPriceException;
import sme.tech.innovators.sme.service.ProductNormalizationHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductNormalizationHelperTest {

    private ProductNormalizationHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ProductNormalizationHelper();
    }

    @Test
    void defaultsCurrencyToZar() {
        assertEquals("ZAR", helper.normalizeCurrency(null));
        assertEquals("ZAR", helper.normalizeCurrency("zar"));
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThrows(InvalidProductPriceException.class, () -> helper.normalizeCurrency("USD"));
    }

    @Test
    void rejectsNegativePrice() {
        assertThrows(InvalidProductPriceException.class, () -> helper.validatePriceAmount(-1));
    }

    @Test
    void rejectsCompareAtNotGreaterThanPrice() {
        assertThrows(InvalidProductPriceException.class,
                () -> helper.validateCompareAtPriceAmount(100, 100));
        assertThrows(InvalidProductPriceException.class,
                () -> helper.validateCompareAtPriceAmount(50, 100));
    }

    @Test
    void acceptsValidCompareAtPrice() {
        assertDoesNotThrow(() -> helper.validateCompareAtPriceAmount(24900, 19900));
        assertDoesNotThrow(() -> helper.validateCompareAtPriceAmount(null, 19900));
    }

    @Test
    void derivesOnSaleFlag() {
        assertTrue(helper.isOnSale(24900, 19900));
        assertFalse(helper.isOnSale(null, 19900));
        assertFalse(helper.isOnSale(19900, 19900));
        assertFalse(helper.isOnSale(100, 19900));
    }

    @Test
    void formatsZarPriceLabel() {
        String label = helper.formatPriceLabel(24900, "ZAR");
        assertNotNull(label);
        assertTrue(label.contains("249"));
    }

    @Test
    void rejectsInvalidImageUrl() {
        assertThrows(InvalidProductDataException.class,
                () -> helper.validateOptionalUrl("ftp://example.com/a.jpg", "imageUrl"));
    }

    @Test
    void acceptsHttpsImageUrl() {
        assertDoesNotThrow(() -> helper.validateOptionalUrl("https://example.com/a.jpg", "imageUrl"));
    }

    @Test
    void validatesGalleryUrls() {
        assertThrows(InvalidProductDataException.class,
                () -> helper.validateGalleryUrls(List.of("https://ok.com/a.jpg", "not-a-url")));
    }
}
