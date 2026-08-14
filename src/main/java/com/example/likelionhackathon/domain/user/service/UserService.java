package com.example.likelionhackathon.domain.user.service;

import com.example.likelionhackathon.domain.auth.entity.EmailVerification;
import com.example.likelionhackathon.domain.auth.repository.EmailVerificationRepository;
import com.example.likelionhackathon.domain.user.dto.UserRequest;
import com.example.likelionhackathon.domain.user.dto.UserResponse;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final long VERIFIED_EMAIL_VALID_MINUTES = 30;

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public UserResponse.Signup signup(UserRequest.Signup request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        EmailVerification emailVerification = emailVerificationRepository.findByEmail(request.email())
                .filter(verification -> verification.isSignupAvailable(
                        OffsetDateTime.now(), VERIFIED_EMAIL_VALID_MINUTES))
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_NOT_VERIFIED));

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.create(
                request.email(),
                encodedPassword,
                request.name()
        );
        User savedUser = userRepository.save(user);
        emailVerificationRepository.delete(emailVerification);

        return new UserResponse.Signup(savedUser.getId());
    }

    @Transactional(readOnly = true)
    public UserResponse.ActivityStatusResult getActivityStatus() {
        return toActivityStatusResult(getCurrentUser());
    }

    @Transactional
    public UserResponse.ActivityStatusResult updateActivityStatus(
            UserRequest.UpdateActivityStatus request
    ) {
        User user = getCurrentUser();
        user.changeActivityStatus(request.status());
        return toActivityStatusResult(user);
    }

    private User getCurrentUser() {
        Long userId = Long.valueOf(currentUserProvider.currentPrincipalKey());
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private UserResponse.ActivityStatusResult toActivityStatusResult(User user) {
        return new UserResponse.ActivityStatusResult(user.getActivityStatus());
    }
}
