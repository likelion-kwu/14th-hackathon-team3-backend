package com.example.likelionhackathon.domain.auth.repository;

import com.example.likelionhackathon.domain.auth.entity.PasswordResetVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetVerificationRepository extends JpaRepository<PasswordResetVerification, Long> {
    Optional<PasswordResetVerification> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PasswordResetVerification p where p.email = :email")
    Optional<PasswordResetVerification> findByEmailForUpdate(@Param("email") String email);
}
