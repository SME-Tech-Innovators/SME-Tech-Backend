package sme.tech.innovators.sme.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DeliveryQuoteRequest {

    @Valid
    @NotNull(message = "collectionAddress is required")
    @JsonProperty("collection_address")
    private CollectionAddress collectionAddress;

    @Valid
    @NotNull(message = "deliveryAddress is required")
    @JsonProperty("delivery_address")
    private DeliveryAddress deliveryAddress;

    @Valid
    @NotEmpty(message = "at least one parcel is required")
    private List<Parcel> parcels;

    @Data
    public static class CollectionAddress {
        @NotBlank(message = "company is required")
        private String company;

        @NotBlank(message = "streetAddress is required")
        @JsonProperty("street_address")
        private String streetAddress;

        @NotBlank(message = "localArea is required")
        @JsonProperty("local_area")
        private String localArea;

        @NotBlank(message = "city is required")
        private String city;

        @NotBlank(message = "zone is required")
        private String zone;

        @NotBlank(message = "postalCode is required")
        @JsonProperty("code")
        private String postalCode;

        @NotBlank(message = "country is required")
        @JsonProperty("country")
        private String country;

        @NotBlank(message = "address type is required")
        @JsonProperty("type")
        private String type; // business or residential
    }

    @Data
    public static class DeliveryAddress {
        @NotBlank(message = "streetAddress is required")
        @JsonProperty("street_address")
        private String streetAddress;

        @NotBlank(message = "localArea is required")
        @JsonProperty("local_area")
        private String localArea;

        @NotBlank(message = "city is required")
        private String city;

        @NotBlank(message = "zone is required")
        private String zone;

        @NotBlank(message = "postalCode is required")
        @JsonProperty("code")
        private String postalCode;

        @NotBlank(message = "country is required")
        private String country;

        @NotBlank(message = "address type is required")
        private String type; // business or residential
    }

    @Data
    public static class Parcel {
        @DecimalMin(value = "0.1", message = "length must be at least 0.1 cm")
        @JsonProperty("submitted_length_cm")
        private BigDecimal lengthCm;

        @DecimalMin(value = "0.1", message = "width must be at least 0.1 cm")
        @JsonProperty("submitted_width_cm")
        private BigDecimal widthCm;

        @DecimalMin(value = "0.1", message = "height must be at least 0.1 cm")
        @JsonProperty("submitted_height_cm")
        private BigDecimal heightCm;

        @DecimalMin(value = "0.01", message = "weight must be greater than zero")
        @JsonProperty("submitted_weight_kg")
        private BigDecimal weightKg;

        private String description;
    }
}