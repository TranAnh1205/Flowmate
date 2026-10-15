package com.smarttask.auth.repository;

import com.smarttask.auth.domain.AdminActionToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AdminActionTokenRepository extends JpaRepository<AdminActionToken, UUID> {
    Optional<AdminActionToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update AdminActionToken token set token.revokedAt = :now " +
            "where token.userId = :userId and token.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
