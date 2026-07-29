package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;
import sme.tech.innovators.sme.entity.ProductStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class UpdateProductRequest {

    private String title;
    private String sku;
    private String slug;

    @Min(value = 0, message = "priceAmount must be >= 0")
    private Integer priceAmount;

    /** Optional compare-at (was) price in minor units; must be > priceAmount when set. */
    @Min(value = 0, message = "compareAtPriceAmount must be >= 0")
    private Integer compareAtPriceAmount;

    /** When true, clears compare-at price (takes precedence over compareAtPriceAmount). */
    private Boolean clearCompareAtPrice;

    private String currency;
    private UUID categoryId;
    private String categoryName;
    private Boolean clearCategory;

    private ProductStatus status;
    private String imageUrl;
    private UUID mainImageId;
    private Boolean clearMainImage;
    private String summary;
    private List<String> galleryUrls;
    private List<UUID> galleryMediaIds;
    private String configurationLabel;
    private String warrantyNote;
    private String shippingNote;
    private Map<String, Object> metadata;
}
