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
public class CreateShipLogicShipmentRequest {

    @Valid
    @NotNull
    @JsonProperty("collection_address")
    private Address collectionAddress;

    @Valid
    @NotNull
    private Contact collectionContact;

    @Valid
    @NotNull
    @JsonProperty("delivery_address")
    private Address deliveryAddress;

    @Valid
    @NotNull
    private Contact deliveryContact;

    @Valid
    @NotEmpty
    private List<Parcel> parcels;

    @JsonProperty("service_level_id")
    private Integer serviceLevelId;

    @JsonProperty("service_level_code")
    private String serviceLevelCode;

    @NotBlank
    private String customerReference;

    private String customerReferenceName = "Order no.";
    private BigDecimal declaredValue;
    private boolean muteNotifications = true;
    private String specialInstructionsCollection;
    private String specialInstructionsDelivery;

    @Data
    public static class Address {
        @NotBlank private String type;
        private String company;
        @NotBlank @JsonProperty("street_address") private String streetAddress;
        @NotBlank @JsonProperty("local_area") private String localArea;
        @NotBlank private String city;
        @NotBlank private String zone;
        @NotBlank private String country;
        @NotBlank @JsonProperty("code") private String postalCode;
        private BigDecimal lat;
        private BigDecimal lng;
    }

    @Data
    public static class Contact {
        @NotBlank private String name;
        private String email;
        @JsonProperty("mobile_number") private String mobileNumber;
    }

    @Data
    public static class Parcel {
        private String parcelDescription;
        @DecimalMin("0.1") @JsonProperty("submitted_length_cm") private BigDecimal lengthCm;
        @DecimalMin("0.1") @JsonProperty("submitted_width_cm") private BigDecimal widthCm;
        @DecimalMin("0.1") @JsonProperty("submitted_height_cm") private BigDecimal heightCm;
        @DecimalMin("0.01") @JsonProperty("submitted_weight_kg") private BigDecimal weightKg;
    }
}