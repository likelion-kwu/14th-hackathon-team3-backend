package com.example.likelionhackathon.global.security.jwt;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {
    private static final String TEST_SECRET =
            "VGhpcy1pcy1hLXRlc3Qtb25seS0zMi1ieXRlLWp3dC1zZWNyZXQ=";
    private static final long EXPIRATION_MS = 3_600_000L;
    private final JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);

    @Test
    void accessTokenContainsUserIdAsSubject() {
        String token = provider.createAccessToken(42L);
        assertThat(token).isNotBlank();
        assertThat(provider.getUserId(token)).isEqualTo(42L);
        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    void accessTokenUsesConfiguredExpiration() {
        String token = provider.createAccessToken(42L);
        long lifetime = Duration.between(provider.getIssuedAt(token), provider.getExpiration(token)).toMillis();
        assertThat(lifetime).isCloseTo(EXPIRATION_MS, org.assertj.core.data.Offset.offset(1_000L));
    }
}
