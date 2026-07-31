package sme.tech.innovators.sme.dto.response;

import lombok.Builder;
import lombok.Data;
import sme.tech.innovators.sme.entity.ProductStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ProductDto {
    private UUID id;
    private UUID workspaceId;
    private String title;
    private String slug;
    private String sku;
    private Integer priceAmount;
    private Integer compareAtPriceAmount;
    private String currency;
    private String priceLabel;
    private String compareAtPriceLabel;
    private Boolean onSale;
    private Integer quantityAvailable;
    private Boolean inStock;
    private CategoryDto category;
    private ProductStatus status;
    private UUID mainImageId;
    private String imageUrl;
    private String summary;
    private List<UUID> galleryMediaIds;
    private List<String> galleryUrls;
    private String configurationLabel;
    private String warrantyNote;
    private String shippingNote;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
