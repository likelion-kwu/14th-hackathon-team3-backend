package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.entity.ProjectEnums.IntegrationProvider;
import com.example.likelionhackathon.domain.project.service.ProjectOAuthProviderRegistry.OAuthProviderSettings;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DefaultProjectOAuthClient implements ProjectOAuthClient {

    private final RestClient restClient;

    public DefaultProjectOAuthClient() {
        this(RestClient.builder());
    }

    DefaultProjectOAuthClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String authorizationUrl(OAuthProviderSettings settings, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(settings.authorizationUri())
                .queryParam("client_id", settings.clientId())
                .queryParam("redirect_uri", settings.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state);

        if (!settings.scopes().isEmpty()) {
            String delimiter = settings.provider() == IntegrationProvider.SLACK ? "," : " ";
            builder.queryParam("scope", String.join(delimiter, settings.scopes()));
        }
        switch (settings.provider()) {
            case GOOGLE_DRIVE -> builder
                    .queryParam("access_type", "offline")
                    .queryParam("prompt", "consent");
            case NOTION -> builder.queryParam("owner", "user");
            default -> {
            }
        }
        return builder.build().encode().toUriString();
    }

    @Override
    public OAuthTokenSet exchangeCode(OAuthProviderSettings settings, String code) {
        try {
            JsonNode response = switch (settings.provider()) {
                case NOTION -> exchangeNotion(settings, code);
                case FIGMA -> exchangeFigma(settings, code);
                case SLACK, TEAMS, GOOGLE_DRIVE -> exchangeForm(settings, code);
            };
            if (response == null
                    || (settings.provider() == IntegrationProvider.SLACK
                    && response.has("ok")
                    && !response.path("ok").asBoolean())) {
                throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            String accessToken = text(response, "access_token");
            if (accessToken == null) {
                throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return new OAuthTokenSet(
                    accessToken,
                    text(response, "refresh_token"),
                    longValue(response, "expires_in"),
                    text(response, "scope"),
                    externalAccountId(settings.provider(), response)
            );
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private JsonNode exchangeForm(OAuthProviderSettings settings, String code) {
        MultiValueMap<String, String> form = baseForm(settings, code);
        if (settings.provider() == IntegrationProvider.SLACK) {
            form.add("client_id", settings.clientId());
            form.add("client_secret", settings.clientSecret());
        } else {
            form.add("client_id", settings.clientId());
            form.add("client_secret", settings.clientSecret());
            if (settings.provider() == IntegrationProvider.TEAMS
                    && !settings.scopes().isEmpty()) {
                form.add("scope", String.join(" ", settings.scopes()));
            }
        }
        return restClient.post()
                .uri(settings.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode exchangeNotion(OAuthProviderSettings settings, String code) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", settings.redirectUri());
        return restClient.post()
                .uri(settings.tokenUri())
                .headers(headers -> headers.setBasicAuth(settings.clientId(), settings.clientSecret()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode exchangeFigma(OAuthProviderSettings settings, String code) {
        return restClient.post()
                .uri(settings.tokenUri())
                .headers(headers -> headers.setBasicAuth(settings.clientId(), settings.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(baseForm(settings, code))
                .retrieve()
                .body(JsonNode.class);
    }

    private MultiValueMap<String, String> baseForm(OAuthProviderSettings settings, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", settings.redirectUri());
        return form;
    }

    private String externalAccountId(IntegrationProvider provider, JsonNode response) {
        return switch (provider) {
            case SLACK -> text(response.path("team"), "id");
            case NOTION -> text(response, "workspace_id");
            case FIGMA -> text(response, "user_id_string");
            case TEAMS, GOOGLE_DRIVE -> null;
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.canConvertToLong() ? value.longValue() : null;
    }
}
