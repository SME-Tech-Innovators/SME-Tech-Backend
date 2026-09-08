package sme.tech.innovators.sme.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sme.tech.innovators.sme.entity.RefreshToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    /** All sessions (active + expired + revoked) for a user — login history. */
    List<RefreshToken> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Alias used by AccountService for backwards compatibility. */
    List<RefreshToken> findAllByUserId(UUID userId);

    /** Only currently active sessions: not revoked and not expired. */
    @Query("SELECT r FROM RefreshToken r WHERE r.userId = :userId AND r.revoked = false AND r.expiresAt > :now")
    List<RefreshToken> findActiveSessionsByUserId(@Param("userId") UUID userId,
                                                  @Param("now") LocalDateTime now);

    /** Revoke all tokens for a user — used when deleting/disabling an account. */
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now WHERE r.userId = :userId AND r.revoked = false")
    @org.springframework.data.jpa.repository.Modifying
    void revokeAllByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    void deleteAllByUserId(UUID userId);
}
