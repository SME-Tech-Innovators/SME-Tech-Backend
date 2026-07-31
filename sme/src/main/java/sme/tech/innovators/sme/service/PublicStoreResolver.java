package sme.tech.innovators.sme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.tech.innovators.sme.entity.Storefront;
import sme.tech.innovators.sme.entity.StorefrontPublishSnapshot;
import sme.tech.innovators.sme.entity.Workspace;
import sme.tech.innovators.sme.entity.WorkspaceStatus;
import sme.tech.innovators.sme.exception.PublicStorefrontNotPublishedException;
import sme.tech.innovators.sme.exception.StoreNotAvailableException;
import sme.tech.innovators.sme.exception.StoreNotFoundException;
import sme.tech.innovators.sme.repository.StorefrontPublishSnapshotRepository;
import sme.tech.innovators.sme.repository.StorefrontRepository;
import sme.tech.innovators.sme.repository.WorkspaceRepository;

/**
 * Shared LIVE published store resolution for public storefront + cart/checkout APIs.
 */
@Service
@RequiredArgsConstructor
public class PublicStoreResolver {

    private final WorkspaceRepository workspaceRepository;
    private final StorefrontRepository storefrontRepository;
    private final StorefrontPublishSnapshotRepository publishSnapshotRepository;

    @Transactional(readOnly = true)
    public LiveStore resolveLiveStore(String storeSlug) {
        if (storeSlug == null || storeSlug.isBlank()) {
            throw new StoreNotFoundException("Store not found");
        }

        Workspace workspace = workspaceRepository.findByPublicSlugIgnoreCase(storeSlug.trim())
                .orElseThrow(() -> new StoreNotFoundException("Store not found: " + storeSlug));

        if (workspace.getStatus() == WorkspaceStatus.UNPUBLISHED
                || workspace.getStatus() == WorkspaceStatus.SUSPENDED) {
            throw new StoreNotAvailableException("Store is not available: " + storeSlug);
        }

        if (workspace.getStatus() != WorkspaceStatus.LIVE) {
            throw new PublicStorefrontNotPublishedException(
                    "Storefront is not published: " + storeSlug);
        }

        Storefront storefront = storefrontRepository.findByWorkspace(workspace)
                .orElseThrow(() -> new PublicStorefrontNotPublishedException(
                        "Storefront is not published: " + storeSlug));

        if (storefront.getPublishedSnapshotId() == null) {
            throw new PublicStorefrontNotPublishedException(
                    "Storefront is not published: " + storeSlug);
        }

        StorefrontPublishSnapshot snapshot = publishSnapshotRepository
                .findById(storefront.getPublishedSnapshotId())
                .orElseThrow(() -> new PublicStorefrontNotPublishedException(
                        "Published snapshot missing for store: " + storeSlug));

        return new LiveStore(workspace, storefront, snapshot);
    }

    @Transactional(readOnly = true)
    public Workspace requireLiveWorkspace(String storeSlug) {
        return resolveLiveStore(storeSlug).workspace();
    }

    public record LiveStore(Workspace workspace, Storefront storefront, StorefrontPublishSnapshot snapshot) {}
}
