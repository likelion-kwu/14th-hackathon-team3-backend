package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectOAuthProviderRegistry {

    private final Environment environment;

    public OAuthProviderSettings require(IntegrationProvider provider) {
        String prefix = "project.oauth." + propertyName(provider) + ".";
        String clientId = environment.getProperty(prefix + "client-id");
        String clientSecret = environment.getProperty(prefix + "client-secret");
        String redirectUri = environment.getProperty(prefix + "redirect-uri");
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(redirectUri)) {
            throw new CustomException(ErrorCode.OAUTH_CONFIGURATION_MISSING);
        }
        return new OAuthProviderSettings(
                provider,
                clientId.trim(),
                clientSecret.trim(),
                redirectUri.trim(),
                environment.getProperty(prefix + "authorization-uri", defaultAuthorizationUri(provider)),
                environment.getProperty(prefix + "token-uri", defaultTokenUri(provider)),
                scopes(environment.getProperty(prefix + "scopes", defaultScopes(provider)))
        );
    }

    private String propertyName(IntegrationProvider provider) {
        return switch (provider) {
            case SLACK -> "slack";
            case TEAMS -> "teams";
            case NOTION -> "notion";
            case FIGMA -> "figma";
            case GOOGLE_DRIVE -> "google-drive";
        };
    }

    private String defaultAuthorizationUri(IntegrationProvider provider) {
        return switch (provider) {
            case SLACK -> "https://slack.com/oauth/v2/authorize";
            case TEAMS -> "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
            case NOTION -> "https://api.notion.com/v1/oauth/authorize";
            case FIGMA -> "https://www.figma.com/oauth";
            case GOOGLE_DRIVE -> "https://accounts.google.com/o/oauth2/v2/auth";
        };
    }

    private String defaultTokenUri(IntegrationProvider provider) {
        return switch (provider) {
            case SLACK -> "https://slack.com/api/oauth.v2.access";
            case TEAMS -> "https://login.microsoftonline.com/common/oauth2/v2.0/token";
            case NOTION -> "https://api.notion.com/v1/oauth/token";
            case FIGMA -> "https://api.figma.com/v1/oauth/token";
            case GOOGLE_DRIVE -> "https://oauth2.googleapis.com/token";
        };
    }

    private String defaultScopes(IntegrationProvider provider) {
        return switch (provider) {
            case SLACK -> "channels:read,channels:history,team:read";
            case TEAMS -> "offline_access User.Read Team.ReadBasic.All Channel.ReadBasic.All";
            case NOTION -> "";
            case FIGMA -> "file_content:read,file_metadata:read";
            case GOOGLE_DRIVE -> "https://www.googleapis.com/auth/drive.readonly";
        };
    }

    private List<String> scopes(String configuredScopes) {
        if (isBlank(configuredScopes)) {
            return List.of();
        }
        return Arrays.stream(configuredScopes.trim().split("[\\s,]+"))
                .filter(scope -> !scope.isBlank())
                .distinct()
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record OAuthProviderSettings(
            IntegrationProvider provider,
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizationUri,
            String tokenUri,
            List<String> scopes
    ) {
    }
}
