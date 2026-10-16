package com.smarttask.auth.repository;

import com.smarttask.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    Optional<RefreshToken> findByIdAndUserId(UUID id, UUID userId);
    List<RefreshToken> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :revokedAt, token.revokeReason = :reason " +
            "where token.userId = :userId and token.revokedAt is null")
    int revokeActiveTokensByUserId(@Param("userId") UUID userId,
                                   @Param("revokedAt") Instant revokedAt,
                                   @Param("reason") String reason);

    default int revokeActiveTokensByUserId(UUID userId, Instant revokedAt) {
        return revokeActiveTokensByUserId(userId, revokedAt, "Thu hồi phiên");
    }
}
