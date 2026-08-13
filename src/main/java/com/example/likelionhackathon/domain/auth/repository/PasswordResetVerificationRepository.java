package com.example.likelionhackathon.domain.auth.repository;

import com.example.likelionhackathon.domain.auth.entity.PasswordResetVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PasswordResetVerificationRepository extends JpaRepository<PasswordResetVerification, Long> {
    Optional<PasswordResetVerification> findByEmail(String email);
}
