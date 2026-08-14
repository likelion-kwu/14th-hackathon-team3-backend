package com.example.likelionhackathon.domain.auth.service;

import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class EmailVerificationMailServiceTest {
    @Mock private JavaMailSender mailSender;

    @Test
    void sendFailureUsesCommonErrorHandling() {
        doThrow(new MailSendException("SMTP failure")).when(mailSender).send(any(SimpleMailMessage.class));
        EmailVerificationMailService service = new EmailVerificationMailService(mailSender);

        assertThatThrownBy(() -> service.sendVerificationCode("user@example.com", "381205"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }
}
