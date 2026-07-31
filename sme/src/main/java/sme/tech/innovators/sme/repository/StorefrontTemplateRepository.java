package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.StorefrontTemplate;
import sme.tech.innovators.sme.entity.StorefrontTemplateStatus;

import java.util.List;

public interface StorefrontTemplateRepository extends JpaRepository<StorefrontTemplate, String> {
    List<StorefrontTemplate> findAllByStatus(StorefrontTemplateStatus status);

    List<StorefrontTemplate> findAllByStatusIn(List<StorefrontTemplateStatus> statuses);
}
