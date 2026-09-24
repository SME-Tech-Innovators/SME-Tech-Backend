package sme.tech.innovators.sme.shipping;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ShippingMoneyUtils {

    private ShippingMoneyUtils() {}

    /** API quote/checkout amounts are minor units (cents). Orders store major units. */
    public static BigDecimal minorToMajor(int minorUnits) {
        return BigDecimal.valueOf(minorUnits).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public static int majorToMinor(BigDecimal majorUnits) {
        if (majorUnits == null) {
            return 0;
        }
        return majorUnits.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
