package com.example.likelionhackathon.global.security.jwt;

import com.example.likelionhackathon.global.security.JwtAuthenticationEntryPoint;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
        JwtAuthenticationEntryPoint entryPoint = mock(JwtAuthenticationEntryPoint.class);
        JwtAuthenticationFilter filter = filter(new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS), entryPoint);
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                chain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(entryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void validBearerTokenCreatesAuthenticationWithUserIdPrincipal() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);
        JwtAuthenticationEntryPoint entryPoint = mock(JwtAuthenticationEntryPoint.class);
        JwtAuthenticationFilter filter = filter(provider, entryPoint);
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
    void invalidTokenInvokesEntryPointAndStopsChain() throws Exception {
        JwtAuthenticationEntryPoint entryPoint = mock(JwtAuthenticationEntryPoint.class);
        JwtAuthenticationFilter filter = filter(new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS), entryPoint);
        MockHttpServletRequest request = bearerRequest("not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, chain(chainCalled));

        assertThat(chainCalled).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(entryPoint).commence(eq(request), eq(response), any(AuthenticationException.class));
    }

    @Test
    void expiredTokenInvokesEntryPointAndStopsChain() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, 1L);
        JwtAuthenticationEntryPoint entryPoint = mock(JwtAuthenticationEntryPoint.class);
        JwtAuthenticationFilter filter = filter(provider, entryPoint);
        MockHttpServletRequest request = bearerRequest(provider.createAccessToken(7L));
        AtomicBoolean chainCalled = new AtomicBoolean();
        Thread.sleep(10L);

        filter.doFilter(request, new MockHttpServletResponse(), chain(chainCalled));

        assertThat(chainCalled).isFalse();
        verify(entryPoint).commence(eq(request), any(), any(AuthenticationException.class));
    }

    @Test
    void emptyBearerTokenInvokesEntryPointAndStopsChain() throws Exception {
        JwtAuthenticationEntryPoint entryPoint = mock(JwtAuthenticationEntryPoint.class);
        JwtAuthenticationFilter filter = filter(new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS), entryPoint);
        MockHttpServletRequest request = bearerRequest("");
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), chain(chainCalled));

        assertThat(chainCalled).isFalse();
        verify(entryPoint).commence(eq(request), any(), any(AuthenticationException.class));
    }

    @Test
    void existingAuthenticationIsNotOverwritten() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken("existing", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);
        JwtAuthenticationEntryPoint entryPoint = mock(JwtAuthenticationEntryPoint.class);
        JwtAuthenticationFilter filter = filter(provider, entryPoint);
        MockHttpServletRequest request = bearerRequest(provider.createAccessToken(42L));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), chain(chainCalled));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    private JwtAuthenticationFilter filter(
            JwtTokenProvider provider,
            JwtAuthenticationEntryPoint entryPoint
    ) {
        return new JwtAuthenticationFilter(provider, entryPoint);
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
