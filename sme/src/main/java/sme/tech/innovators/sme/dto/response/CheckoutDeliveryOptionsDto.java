package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * All available delivery options returned to the customer checkout UI.
 *
 * <p>Each element in {@code options} represents one delivery provider
 * (currently only Uber Direct).  The frontend renders these as selectable
 * shipping methods alongside their fees.
 */
@Data
@Builder
public class CheckoutDeliveryOptionsDto {

    /** Store public slug — echoed back for the frontend to confirm context. */
    private String storeSlug;

    /** Non-empty list of available delivery options. May be empty if the store
     *  has not enabled any delivery services, or no couriers cover the dropoff area. */
    private List<UberDirectQuoteDto> options;
}
