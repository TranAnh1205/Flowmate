package com.smarttask.auth.repository;

import com.smarttask.auth.domain.User;
import com.smarttask.auth.domain.AccountStatus;
import com.smarttask.auth.domain.UserRole;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRoleAndStatus(UserRole role, AccountStatus status);
    long countByStatus(AccountStatus status);
    long countByRole(UserRole role);
    long countByCreatedAtGreaterThanEqual(Instant since);
}
