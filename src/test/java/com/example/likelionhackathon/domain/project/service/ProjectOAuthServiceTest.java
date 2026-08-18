package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.dto.ProjectRequest;
import com.example.likelionhackathon.domain.project.dto.ProjectResponse;
import com.example.likelionhackathon.domain.project.entity.Project;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectIntegration;
import com.example.likelionhackathon.domain.project.entity.ProjectOAuthState;
import com.example.likelionhackathon.domain.project.repository.ProjectIntegrationRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectOAuthStateRepository;
import com.example.likelionhackathon.domain.project.service.ProjectOAuthClient.OAuthTokenSet;
import com.example.likelionhackathon.domain.project.service.ProjectOAuthProviderRegistry.OAuthProviderSettings;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectOAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T06:00:00Z");

    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectIntegrationRepository integrationRepository;
    @Mock
    private ProjectOAuthStateRepository stateRepository;
    @Mock
    private ProjectOAuthProviderRegistry providerRegistry;
    @Mock
    private ProjectOAuthClient oauthClient;
    @Mock
    private OAuthTokenCipher tokenCipher;

    private ProjectOAuthService oauthService;

    @BeforeEach
    void setUp() {
        oauthService = new ProjectOAuthService(
                projectAccessService,
                integrationRepository,
                stateRepository,
                providerRegistry,
                oauthClient,
                tokenCipher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void startIssuesOneTimeStateAndAuthorizationUrl() {
        Project project = project();
        OAuthProviderSettings settings = settings();
        when(projectAccessService.findProject(10L)).thenReturn(project);
        when(tokenCipher.isConfigured()).thenReturn(true);
        when(providerRegistry.require(IntegrationProvider.SLACK)).thenReturn(settings);
        when(oauthClient.authorizationUrl(any(), any())).thenReturn("https://slack.com/oauth/test");

        ProjectResponse.OAuthStarted response = oauthService.start(10L, IntegrationProvider.SLACK);

        assertThat(response.authorizationUrl()).isEqualTo("https://slack.com/oauth/test");
        assertThat(response.expiresAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(10));
        ArgumentCaptor<ProjectOAuthState> captor = ArgumentCaptor.forClass(ProjectOAuthState.class);
        verify(stateRepository).save(captor.capture());
        assertThat(captor.getValue().getStateHash()).hasSize(64);
    }

    @Test
    void completeExchangesAndEncryptsTokens() {
        Project project = project();
        OAuthProviderSettings settings = settings();
        ProjectOAuthState state = ProjectOAuthState.issue(
                project,
                IntegrationProvider.SLACK,
                sha256("valid-state"),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(1)
        );
        when(projectAccessService.findProject(10L)).thenReturn(project);
        when(tokenCipher.isConfigured()).thenReturn(true);
        when(stateRepository.findByStateHashAndProjectIdAndProvider(
                sha256("valid-state"), 10L, IntegrationProvider.SLACK
        )).thenReturn(Optional.of(state));
        when(providerRegistry.require(IntegrationProvider.SLACK)).thenReturn(settings);
        when(oauthClient.exchangeCode(settings, "oauth-code")).thenReturn(new OAuthTokenSet(
                "access-token", "refresh-token", 3_600L, "channels:read", "T123"
        ));
        when(tokenCipher.encrypt("access-token")).thenReturn("encrypted-access");
        when(tokenCipher.encrypt("refresh-token")).thenReturn("encrypted-refresh");
        when(integrationRepository.save(any(ProjectIntegration.class))).thenAnswer(invocation -> {
            ProjectIntegration integration = invocation.getArgument(0);
            ReflectionTestUtils.setField(integration, "id", 77L);
            return integration;
        });

        ProjectResponse.OAuthConnected response = oauthService.complete(
                10L,
                IntegrationProvider.SLACK,
                new ProjectRequest.CompleteIntegrationOAuth(
                        "oauth-code", "valid-state", List.of("general"), 30
                )
        );

        assertThat(response.integrationId()).isEqualTo(77L);
        assertThat(response.status()).isEqualTo(IntegrationStatus.CONNECTED);
        assertThat(state.getConsumedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        ArgumentCaptor<ProjectIntegration> captor = ArgumentCaptor.forClass(ProjectIntegration.class);
        verify(integrationRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedAccessToken()).isEqualTo("encrypted-access");
        assertThat(captor.getValue().getEncryptedRefreshToken()).isEqualTo("encrypted-refresh");
        assertThat(captor.getValue().getTokenExpiresAt())
                .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(1));
    }

    @Test
    void completeRejectsExpiredOrUnknownState() {
        when(projectAccessService.findProject(10L)).thenReturn(project());
        when(tokenCipher.isConfigured()).thenReturn(true);

        assertThatThrownBy(() -> oauthService.complete(
                10L,
                IntegrationProvider.SLACK,
                new ProjectRequest.CompleteIntegrationOAuth(
                        "oauth-code", "unknown-state", null, null
                )
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OAUTH_STATE_INVALID);
    }

    private OAuthProviderSettings settings() {
        return new OAuthProviderSettings(
                IntegrationProvider.SLACK,
                "client-id",
                "client-secret",
                "https://frontend.example.com/oauth/callback/slack",
                "https://slack.com/oauth/v2/authorize",
                "https://slack.com/api/oauth.v2.access",
                List.of("channels:read")
        );
    }

    private Project project() {
        Workspace workspace = Workspace.create(
                "Workspace", "RELAI-KR-OAUTH", "RelAI", "KR", List.of()
        );
        Project project = Project.create(
                workspace,
                "OAuth Project",
                "Connect services",
                java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 31)
        );
        ReflectionTestUtils.setField(project, "id", 10L);
        return project;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
