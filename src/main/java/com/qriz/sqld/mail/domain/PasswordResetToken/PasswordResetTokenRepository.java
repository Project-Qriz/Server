package com.qriz.sqld.mail.domain.PasswordResetToken;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByEmailAndTokenAndUsedFalse(String email, String token);
    
    @Modifying
    @Transactional
    void deleteByEmail(String email);
}
