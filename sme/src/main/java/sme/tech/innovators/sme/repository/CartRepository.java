package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.Cart;
import sme.tech.innovators.sme.entity.CartStatus;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByIdAndWorkspaceIdAndStatus(UUID id, UUID workspaceId, CartStatus status);
}
