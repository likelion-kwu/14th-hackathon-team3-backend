package com.example.likelionhackathon.domain.auth.service;

import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetMailService {
    private final JavaMailSender mailSender;

    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[likelion-hackathon] 비밀번호 재설정 인증번호");
        message.setText("비밀번호 재설정 인증번호는 " + code + " 입니다.\n인증번호는 5분 동안 유효합니다.");
        try { mailSender.send(message); }
        catch (MailException e) { throw new CustomException(ErrorCode.EMAIL_SEND_FAILED); }
    }
}
