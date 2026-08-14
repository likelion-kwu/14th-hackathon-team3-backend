package com.example.likelionhackathon.global.security.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET =
            "VGhpcy1pcy1hLXRlc3Qtb25seS0zMi1ieXRlLWp3dC1zZWNyZXQ=";
    private static final long EXPIRATION_MS = 3_600_000L;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthorizationHeaderContinuesWithoutAuthentication() throws Exception {
        JwtAuthenticationFilter filter = filter(new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validBearerTokenCreatesAuthenticationWithUserIdPrincipal() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);
        JwtAuthenticationFilter filter = filter(provider);
        MockHttpServletRequest request = bearerRequest(provider.createAccessToken(42L));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), chain(chainCalled));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(chainCalled).isTrue();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void invalidTokenContinuesWithoutAuthentication() throws Exception {
        JwtAuthenticationFilter filter = filter(new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS));
        MockHttpServletRequest request = bearerRequest("not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, chain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredTokenContinuesWithoutAuthentication() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, -60_000L);
        JwtAuthenticationFilter filter = filter(provider);
        MockHttpServletRequest request = bearerRequest(provider.createAccessToken(7L));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), chain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void emptyBearerTokenContinuesWithoutAuthentication() throws Exception {
        JwtAuthenticationFilter filter = filter(new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS));
        MockHttpServletRequest request = bearerRequest("");
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), chain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void existingAuthenticationIsNotOverwritten() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken("existing", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);
        JwtAuthenticationFilter filter = filter(provider);
        MockHttpServletRequest request = bearerRequest(provider.createAccessToken(42L));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), chain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    private JwtAuthenticationFilter filter(JwtTokenProvider provider) {
        return new JwtAuthenticationFilter(provider);
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private FilterChain chain(AtomicBoolean called) {
        return (request, response) -> called.set(true);
    }
}
