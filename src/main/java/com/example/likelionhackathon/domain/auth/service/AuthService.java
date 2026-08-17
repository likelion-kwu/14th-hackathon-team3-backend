package com.example.likelionhackathon.domain.auth.service;

import com.example.likelionhackathon.domain.auth.dto.AuthRequest;
import com.example.likelionhackathon.domain.auth.dto.AuthResponse;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import com.example.likelionhackathon.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public AuthResponse.Login login(AuthRequest.Login request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_LOGIN_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
        }

        user.changeActivityStatus(ActivityStatus.ACTIVE);
        return new AuthResponse.Login(user.getId(), jwtTokenProvider.createAccessToken(user.getId()));
    }

    @Transactional
    public void logout() {
        Long userId = Long.valueOf(currentUserProvider.currentPrincipalKey());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.changeActivityStatus(ActivityStatus.OFF);
    }
}
