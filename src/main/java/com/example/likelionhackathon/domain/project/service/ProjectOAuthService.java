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
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectOAuthService {

    private static final int STATE_TTL_MINUTES = 10;
    private static final int MAX_SYNC_INTERVAL_MINUTES = 1_440;

    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository integrationRepository;
    private final ProjectOAuthStateRepository stateRepository;
    private final ProjectOAuthProviderRegistry providerRegistry;
    private final ProjectOAuthClient oauthClient;
    private final OAuthTokenCipher tokenCipher;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ProjectResponse.OAuthStarted start(Long projectId, IntegrationProvider provider) {
        Project project = projectAccessService.findProject(projectId);
        projectAccessService.requireAdmin(projectId);
        if (provider == null || !tokenCipher.isConfigured()) {
            throw new CustomException(ErrorCode.OAUTH_CONFIGURATION_MISSING);
        }
        OAuthProviderSettings settings = providerRegistry.require(provider);
        OffsetDateTime expiresAt = now().plusMinutes(STATE_TTL_MINUTES);
        String state = newState();
        stateRepository.save(ProjectOAuthState.issue(
                project,
                provider,
                hash(state),
                expiresAt
        ));
        return new ProjectResponse.OAuthStarted(
                oauthClient.authorizationUrl(settings, state),
                expiresAt
        );
    }

    @Transactional
    public ProjectResponse.OAuthConnected complete(
            Long projectId,
            IntegrationProvider provider,
            ProjectRequest.CompleteIntegrationOAuth request
    ) {
        Project project = projectAccessService.findProject(projectId);
        projectAccessService.requireAdmin(projectId);
        validate(request, provider);
        if (!tokenCipher.isConfigured()) {
            throw new CustomException(ErrorCode.OAUTH_CONFIGURATION_MISSING);
        }
        if (integrationRepository.existsByProjectIdAndProviderAndStatus(
                projectId,
                provider,
                IntegrationStatus.CONNECTED
        )) {
            throw new CustomException(ErrorCode.INVALID_INTEGRATION_ACTION);
        }

        OffsetDateTime now = now();
        ProjectOAuthState state = stateRepository.findByStateHashAndProjectIdAndProvider(
                        hash(request.state()),
                        projectId,
                        provider
                )
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new CustomException(ErrorCode.OAUTH_STATE_INVALID));
        OAuthProviderSettings settings = providerRegistry.require(provider);
        OAuthTokenSet tokenSet = oauthClient.exchangeCode(settings, request.code().trim());
        if (tokenSet.accessToken() == null || tokenSet.accessToken().isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        OffsetDateTime tokenExpiresAt = tokenSet.expiresInSeconds() == null
                ? null
                : now.plusSeconds(tokenSet.expiresInSeconds());
        ProjectIntegration integration = ProjectIntegration.connect(
                provider,
                normalizeResources(request.resourceIds()),
                request.syncIntervalMinutes(),
                tokenCipher.encrypt(tokenSet.accessToken()),
                tokenCipher.encrypt(tokenSet.refreshToken()),
                tokenExpiresAt,
                tokenSet.grantedScopes(),
                tokenSet.externalAccountId()
        );
        project.addIntegration(integration);
        ProjectIntegration saved = integrationRepository.save(integration);
        state.consume(now);
        return new ProjectResponse.OAuthConnected(
                saved.getId(),
                saved.getProvider(),
                saved.getStatus()
        );
    }

    private void validate(
            ProjectRequest.CompleteIntegrationOAuth request,
            IntegrationProvider provider
    ) {
        if (provider == null
                || request == null
                || request.code() == null
                || request.code().isBlank()
                || request.state() == null
                || request.state().isBlank()
                || invalidInterval(request.syncIntervalMinutes())
                || (request.resourceIds() != null
                && request.resourceIds().stream()
                .anyMatch(resource -> resource == null || resource.isBlank()))) {
            throw new CustomException(ErrorCode.INVALID_INTEGRATION_ACTION);
        }
    }

    private boolean invalidInterval(Integer interval) {
        return interval != null && (interval < 1 || interval > MAX_SYNC_INTERVAL_MINUTES);
    }

    private List<String> normalizeResources(List<String> resources) {
        if (resources == null) {
            return List.of();
        }
        return new LinkedHashSet<>(resources.stream().map(String::trim).toList()).stream().toList();
    }

    private String newState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
