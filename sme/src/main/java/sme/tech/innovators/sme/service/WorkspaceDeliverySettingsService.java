package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.config.UberDirectProperties;
import sme.tech.innovators.sme.dto.request.UpdateDeliverySettingsRequest;
import sme.tech.innovators.sme.dto.response.DeliverySettingsDto;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.entity.WorkspaceDeliverySettings;
import sme.tech.innovators.sme.exception.WorkspaceNotFoundException;
import sme.tech.innovators.sme.repository.WorkspaceDeliverySettingsRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

import java.util.UUID;

/**
 * CRUD service for {@link WorkspaceDeliverySettings}.
 *
 * <p>Merchants call this to:
 * <ul>
 *   <li>View the current delivery configuration for their workspace.</li>
 *   <li>Enable / disable Uber Direct.</li>
 *   <li>Set the pickup address that couriers will collect orders from.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceDeliverySettingsService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceDeliverySettingsRepository settingsRepository;
    private final UberDirectProperties uberDirectProperties;

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Retrieve delivery settings for a workspace.
     * If no settings row exists yet, a default (all-disabled) response is
     * returned — the row is created lazily on the first update.
     */
    @Transactional(readOnly = true)
    public DeliverySettingsDto getSettings(UUID workspaceId, UUID userId) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);
        WorkspaceDeliverySettings settings = settingsRepository
                .findByWorkspaceId(workspace.getId())
                .orElse(null);
        return toDto(workspace, settings);
    }

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Create-or-update delivery settings for a workspace.
     *
     * <p>A {@link WorkspaceDeliverySettings} row is upserted: if none
     * exists it is created, otherwise the existing one is updated in place.
     */
    @Transactional
    public DeliverySettingsDto updateSettings(UUID workspaceId, UUID userId,
                                               UpdateDeliverySettingsRequest request) {
        Workspace workspace = loadOwnedWorkspace(workspaceId, userId);

        WorkspaceDeliverySettings settings = settingsRepository
                .findByWorkspaceId(workspace.getId())
                .orElseGet(() -> WorkspaceDeliverySettings.builder()
                        .workspace(workspace)
                        .build());

        // Provider toggles
        settings.setUberDirectEnabled(Boolean.TRUE.equals(request.getUberDirectEnabled()));

        // Pickup address
        settings.setPickupAddressLine1(trimToNull(request.getPickupAddressLine1()));
        settings.setPickupAddressLine2(trimToNull(request.getPickupAddressLine2()));
        settings.setPickupCity(trimToNull(request.getPickupCity()));
        settings.setPickupProvince(trimToNull(request.getPickupProvince()));
        settings.setPickupPostalCode(trimToNull(request.getPickupPostalCode()));
        settings.setPickupCountry(
                request.getPickupCountry() != null
                        ? request.getPickupCountry().trim().toUpperCase()
                        : "ZA");
        settings.setPickupLatitude(request.getPickupLatitude());
        settings.setPickupLongitude(request.getPickupLongitude());
        settings.setPickupContactName(trimToNull(request.getPickupContactName()));
        settings.setPickupContactPhone(trimToNull(request.getPickupContactPhone()));

        settings = settingsRepository.save(settings);
        log.info("Delivery settings updated for workspace={} uberDirectEnabled={}",
                workspaceId, settings.isUberDirectEnabled());

        return toDto(workspace, settings);
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private DeliverySettingsDto toDto(Workspace workspace, WorkspaceDeliverySettings s) {
        boolean uberEnabled = s != null && s.isUberDirectEnabled();
        boolean uberAvailable = uberEnabled
                && (uberDirectProperties.isConfigured() || uberDirectProperties.isMockMode());

        DeliverySettingsDto.DeliverySettingsDtoBuilder b = DeliverySettingsDto.builder()
                .workspaceId(workspace.getId().toString())
                .uberDirectEnabled(uberEnabled)
                .uberDirectAvailable(uberAvailable);

        if (s != null) {
            b.pickupAddressLine1(s.getPickupAddressLine1())
             .pickupAddressLine2(s.getPickupAddressLine2())
             .pickupCity(s.getPickupCity())
             .pickupProvince(s.getPickupProvince())
             .pickupPostalCode(s.getPickupPostalCode())
             .pickupCountry(s.getPickupCountry())
             .pickupLatitude(s.getPickupLatitude())
             .pickupLongitude(s.getPickupLongitude())
             .pickupContactName(s.getPickupContactName())
             .pickupContactPhone(s.getPickupContactPhone())
             .updatedAt(s.getUpdatedAt());
        }

        return b.build();
    }

    // ── Guards ────────────────────────────────────────────────────────────

    private Workspace loadOwnedWorkspace(UUID workspaceId, UUID userId) {
        return workspaceRepository.findByIdAndBusiness_Owner_Id(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found or you do not have access to it"));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
