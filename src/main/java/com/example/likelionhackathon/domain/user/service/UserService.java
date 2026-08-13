package com.example.likelionhackathon.domain.user.service;

import com.example.likelionhackathon.domain.auth.entity.EmailVerification;
import com.example.likelionhackathon.domain.auth.repository.EmailVerificationRepository;
import com.example.likelionhackathon.domain.user.dto.UserRequest;
import com.example.likelionhackathon.domain.user.dto.UserResponse;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse.Signup signup(UserRequest.Signup request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        EmailVerification emailVerification = emailVerificationRepository.findByEmail(request.email())
                .filter(EmailVerification::isVerified)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_NOT_VERIFIED));

        if (!request.password().equals(request.passwordConfirm())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

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
}
