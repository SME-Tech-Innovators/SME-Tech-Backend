package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.StorefrontTemplate;
import sme.tech.innovators.sme.entity.StorefrontTemplateVersion;

import java.util.Optional;
import java.util.UUID;

public interface StorefrontTemplateVersionRepository extends JpaRepository<StorefrontTemplateVersion, UUID> {
    Optional<StorefrontTemplateVersion> findByTemplateAndVersion(StorefrontTemplate template, Integer version);
    Optional<StorefrontTemplateVersion> findByTemplateIdAndVersion(String templateId, Integer version);
}
