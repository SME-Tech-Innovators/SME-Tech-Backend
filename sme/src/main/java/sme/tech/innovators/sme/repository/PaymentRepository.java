package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.tech.innovators.sme.entity.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    java.util.List<Payment> findByOrderIdAndStatus(UUID orderId, sme.tech.innovators.sme.entity.PaymentRecordStatus status);

    Optional<Payment> findByProviderReference(String providerReference);

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
