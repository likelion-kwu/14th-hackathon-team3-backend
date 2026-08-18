package com.example.likelionhackathon.domain.project.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTokenCipherTest {

    @Test
    void encryptUsesVersionedCiphertextWithoutPlainToken() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        OAuthTokenCipher cipher = new OAuthTokenCipher(key);

        String encrypted = cipher.encrypt("secret-oauth-token");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("secret-oauth-token");
    }
}
