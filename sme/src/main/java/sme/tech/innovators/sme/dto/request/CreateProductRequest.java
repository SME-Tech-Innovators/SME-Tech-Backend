package sme.tech.innovators.sme.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import sme.tech.innovators.sme.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateProductRequest {

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "sku is required")
    private String sku;

    private String slug;

    @NotNull(message = "priceAmount is required")
    @DecimalMin(value = "0.00", message = "priceAmount must be >= 0")
    private BigDecimal priceAmount;

    /** Optional compare-at (was) price; must be > priceAmount when set. */
    @DecimalMin(value = "0.00", message = "compareAtPriceAmount must be >= 0")
    private BigDecimal compareAtPriceAmount;

    /** Units available to sell. Required; integer ≥ 0. */
    @NotNull(message = "quantityAvailable is required")
    @Min(value = 0, message = "quantityAvailable must be >= 0")
    private Integer quantityAvailable;

    private String currency;

    private UUID categoryId;
    private String categoryName;

    private ProductStatus status;

    private String imageUrl;
    private UUID mainImageId;
    private String summary;
    private List<String> galleryUrls;
    private List<UUID> galleryMediaIds;
    private String configurationLabel;
    private String warrantyNote;
    private String shippingNote;
    private Map<String, Object> metadata;
}
