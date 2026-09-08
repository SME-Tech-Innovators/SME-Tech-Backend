package sme.tech.innovators.sme.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sme.tech.innovators.sme.exception.InvalidProductDataException;
import sme.tech.innovators.sme.exception.InvalidProductPriceException;
import sme.tech.innovators.sme.service.ProductNormalizationHelper;

import java.math.BigDecimal;
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
        assertThrows(InvalidProductPriceException.class,
                () -> helper.validatePriceAmount(new BigDecimal("-1")));
    }

    @Test
    void rejectsCompareAtNotGreaterThanPrice() {
        assertThrows(InvalidProductPriceException.class,
                () -> helper.validateCompareAtPriceAmount(new BigDecimal("100.00"), new BigDecimal("100.00")));
        assertThrows(InvalidProductPriceException.class,
                () -> helper.validateCompareAtPriceAmount(new BigDecimal("50.00"), new BigDecimal("100.00")));
    }

    @Test
    void acceptsValidCompareAtPrice() {
        assertDoesNotThrow(() -> helper.validateCompareAtPriceAmount(new BigDecimal("249.00"), new BigDecimal("199.00")));
        assertDoesNotThrow(() -> helper.validateCompareAtPriceAmount(null, new BigDecimal("199.00")));
    }

    @Test
    void derivesOnSaleFlag() {
        assertTrue(helper.isOnSale(new BigDecimal("249.00"), new BigDecimal("199.00")));
        assertFalse(helper.isOnSale(null, new BigDecimal("199.00")));
        assertFalse(helper.isOnSale(new BigDecimal("199.00"), new BigDecimal("199.00")));
        assertFalse(helper.isOnSale(new BigDecimal("1.00"), new BigDecimal("199.00")));
    }

    @Test
    void formatsZarPriceLabel() {
        String label = helper.formatPriceLabel(new BigDecimal("249.00"), "ZAR");
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
