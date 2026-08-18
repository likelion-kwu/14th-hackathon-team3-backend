package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.service.ProjectOAuthClient.OAuthTokenSet;
import com.example.likelionhackathon.domain.project.service.ProjectOAuthProviderRegistry.OAuthProviderSettings;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultProjectOAuthClientTest {

    @Test
    void authorizationUrlAddsProviderSpecificParameters() {
        DefaultProjectOAuthClient client = new DefaultProjectOAuthClient(RestClient.builder());

        String slackUrl = decoded(client.authorizationUrl(
                settings(IntegrationProvider.SLACK, List.of("channels:read", "channels:history")),
                "state-value"
        ));
        String notionUrl = decoded(client.authorizationUrl(
                settings(IntegrationProvider.NOTION, List.of()),
                "state-value"
        ));
        String googleUrl = decoded(client.authorizationUrl(
                settings(IntegrationProvider.GOOGLE_DRIVE, List.of("drive.readonly")),
                "state-value"
        ));

        assertThat(slackUrl).contains("scope=channels:read,channels:history");
        assertThat(notionUrl).contains("owner=user");
        assertThat(googleUrl).contains("access_type=offline", "prompt=consent");
    }

    @Test
    void googleTokenExchangeDoesNotResendAuthorizationScopes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DefaultProjectOAuthClient client = new DefaultProjectOAuthClient(builder);
        OAuthProviderSettings settings = settings(
                IntegrationProvider.GOOGLE_DRIVE,
                List.of("https://www.googleapis.com/auth/drive.readonly")
        );

        server.expect(once(), requestTo(settings.tokenUri()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(not(containsString("scope="))))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "refresh_token": "refresh-token",
                          "expires_in": 3600,
                          "scope": "https://www.googleapis.com/auth/drive.readonly"
                        }
                        """, MediaType.APPLICATION_JSON));

        OAuthTokenSet tokenSet = client.exchangeCode(settings, "oauth-code");

        assertThat(tokenSet.accessToken()).isEqualTo("access-token");
        assertThat(tokenSet.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokenSet.expiresInSeconds()).isEqualTo(3_600L);
        server.verify();
    }

    private OAuthProviderSettings settings(
            IntegrationProvider provider,
            List<String> scopes
    ) {
        String providerName = provider.name().toLowerCase();
        return new OAuthProviderSettings(
                provider,
                "client-id",
                "client-secret",
                "https://frontend.example.com/oauth/callback/" + providerName,
                "https://auth.example.com/" + providerName,
                "https://token.example.com/" + providerName,
                scopes
        );
    }

    private String decoded(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
