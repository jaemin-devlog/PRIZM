package com.prizm.user.repository;

import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByRole(UserRole role);
}
