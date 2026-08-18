package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.service.ProjectOAuthProviderRegistry.OAuthProviderSettings;

public interface ProjectOAuthClient {

    String authorizationUrl(OAuthProviderSettings settings, String state);

    OAuthTokenSet exchangeCode(OAuthProviderSettings settings, String code);

    record OAuthTokenSet(
            String accessToken,
            String refreshToken,
            Long expiresInSeconds,
            String grantedScopes,
            String externalAccountId
    ) {
    }
}
