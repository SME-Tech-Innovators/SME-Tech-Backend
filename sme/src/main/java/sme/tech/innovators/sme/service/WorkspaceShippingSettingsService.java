package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.dto.common.ShippingAddressDto;
import sme.tech.innovators.sme.dto.request.UpdateShippingSettingsRequest;
import sme.tech.innovators.sme.dto.response.ShippingSettingsDto;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.entity.WorkspaceShippingSettings;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.WorkspaceRepository;
import sme.tech.innovators.sme.repository.WorkspaceShippingSettingsRepository;
import sme.tech.innovators.sme.shipping.ShippingMoneyUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceShippingSettingsService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceShippingSettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public ShippingSettingsDto getSettings(UUID workspaceId, UUID userId) {
        loadOwnedWorkspace(workspaceId, userId);
        return settingsRepository.findById(workspaceId)
                .map(this::toDto)
                .orElseGet(this::defaultDto);
    }

    @Transactional
    public ShippingSettingsDto updateSettings(UUID workspaceId,
                                               UUID userId,
                                               UpdateShippingSettingsRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        WorkspaceShippingSettings settings = settingsRepository.findById(workspaceId)
                .orElseGet(() -> newShippingSettings(workspace));

        if (request.getProvider() != null && !request.getProvider().isBlank()) {
            settings.setProvider(request.getProvider().trim());
        }
        if (request.getEnabled() != null) {
            settings.setEnabled(request.getEnabled());
        }
        if (request.getCollectionAddress() != null) {
            settings.setCollectionAddress(toAddressMap(request.getCollectionAddress()));
        }
        if (request.getFallbackFlatRateAmount() != null) {
            // Dashboard sends major units (e.g. 99.00 ZAR); DB stores major like orders.
            settings.setFallbackFlatRateAmount(
                    request.getFallbackFlatRateAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (request.getFallbackFlatRateCurrency() != null) {
            settings.setFallbackFlatRateCurrency(request.getFallbackFlatRateCurrency().trim());
        }
        if (request.getAllowPickup() != null) {
            settings.setAllowPickup(request.getAllowPickup());
        }
        if (request.getPickupLabel() != null) {
            settings.setPickupLabel(blankToNull(request.getPickupLabel()));
        }

        settingsRepository.save(settings);
        return toDto(settings);
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceShippingSettings> findSettings(UUID workspaceId) {
        return settingsRepository.findById(workspaceId);
    }

    private ShippingSettingsDto toDto(WorkspaceShippingSettings settings) {
        Integer fallbackMinor = settings.getFallbackFlatRateAmount() != null
                ? ShippingMoneyUtils.majorToMinor(settings.getFallbackFlatRateAmount())
                : null;
        return ShippingSettingsDto.builder()
                .provider(settings.getProvider())
                .enabled(settings.isEnabled())
                .collectionAddress(fromAddressMap(settings.getCollectionAddress()))
                .fallbackFlatRateAmount(fallbackMinor)
                .fallbackFlatRateCurrency(settings.getFallbackFlatRateCurrency())
                .allowPickup(settings.isAllowPickup())
                .pickupLabel(settings.getPickupLabel())
                .build();
    }

    private ShippingSettingsDto defaultDto() {
        return ShippingSettingsDto.builder()
                .provider("bobgo")
                .enabled(false)
                .allowPickup(false)
                .fallbackFlatRateCurrency("ZAR")
                .build();
    }

    static Map<String, Object> toAddressMap(ShippingAddressDto dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line1", dto.getLine1());
        map.put("line2", dto.getLine2() != null ? dto.getLine2() : "");
        map.put("city", dto.getCity());
        map.put("province", dto.getProvince() != null ? dto.getProvince() : "");
        map.put("postalCode", dto.getPostalCode() != null ? dto.getPostalCode() : "");
        map.put("country", dto.getCountry());
        return map;
    }

    static ShippingAddressDto fromAddressMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        ShippingAddressDto dto = new ShippingAddressDto();
        dto.setLine1(stringVal(map.get("line1")));
        dto.setLine2(stringVal(map.get("line2")));
        dto.setCity(stringVal(map.get("city")));
        dto.setProvince(stringVal(map.get("province")));
        dto.setPostalCode(stringVal(map.get("postalCode")));
        dto.setCountry(stringVal(map.get("country")));
        return dto;
    }

    /** @MapsId: set workspace only — do not pre-set workspaceId or save() will merge and fail. */
    private static WorkspaceShippingSettings newShippingSettings(Workspace workspace) {
        WorkspaceShippingSettings settings = new WorkspaceShippingSettings();
        settings.setWorkspace(workspace);
        return settings;
    }

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
