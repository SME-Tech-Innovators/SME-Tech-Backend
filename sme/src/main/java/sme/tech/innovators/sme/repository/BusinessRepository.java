package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.Business;
import sme.tech.innovators.sme.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    boolean existsBySlugAndIsDeletedFalse(String slug);
    Optional<Business> findBySlugAndIsDeletedFalse(String slug);
    /**
     * Use findFirst… because legacy/test data may contain more than one business
     * per owner, which breaks Optional unique-result queries.
     */
    Optional<Business> findFirstByOwnerAndIsDeletedFalseOrderByCreatedAtAsc(User owner);
    List<Business> findAllByOwnerId(UUID ownerId);
    List<Business> findAllByIsDeletedFalse();
}
