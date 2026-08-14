package com.example.likelionhackathon.domain.auth.repository;

import com.example.likelionhackathon.domain.auth.entity.EmailVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailVerification e where e.email = :email")
    Optional<EmailVerification> findByEmailForUpdate(@Param("email") String email);
}
