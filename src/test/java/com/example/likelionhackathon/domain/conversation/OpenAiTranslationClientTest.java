package com.example.likelionhackathon.domain.conversation;

import com.example.likelionhackathon.domain.conversation.service.OpenAiTranslationClient;
import com.example.likelionhackathon.domain.conversation.service.OpenAiTranslationClient.TranslationResult;
import com.example.likelionhackathon.global.config.OpenAiProperties;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiTranslationClientTest {

    @Test
    void parsesStructuredTranslationOutputAndSendsLanguages() {
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sourceLanguage")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("targetLanguage")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("senderDate is the final absolute date")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("what's causing the delay")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nuanceLanguage")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("maxLength")))
                .andRespond(withSuccess(response("Could you review this?", "정중하게 조정했습니다."),
                        MediaType.APPLICATION_JSON));

        TranslationResult result = fixture.client().translate("검토해주세요.", "ko", "en");

        assertThat(result.translatedContent()).isEqualTo("Could you review this?");
        assertThat(result.nuance()).isEqualTo("정중하게 조정했습니다.");
        fixture.server().verify();
    }

    @Test
    void rejectsMissingOrMalformedOutput() {
        TestClient missing = client();
        missing.server().expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("{\"output\":[]}", MediaType.APPLICATION_JSON));
        assertError(missing.client(), ErrorCode.AI_TRANSLATION_FAILED);

        TestClient malformed = client();
        malformed.server().expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("""
                        {"output":[{"content":[{"type":"output_text","text":"not-json"}]}]}
                        """, MediaType.APPLICATION_JSON));
        assertError(malformed.client(), ErrorCode.AI_TRANSLATION_FAILED);
    }

    @Test
    void rejectsBlankOrOversizedTranslatedContent() {
        TestClient blank = client();
        blank.server().expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(response(" ", "설명"), MediaType.APPLICATION_JSON));
        assertError(blank.client(), ErrorCode.AI_TRANSLATION_FAILED);

        TestClient oversized = client();
        oversized.server().expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(response("a".repeat(4001), "설명"), MediaType.APPLICATION_JSON));
        assertError(oversized.client(), ErrorCode.AI_TRANSLATION_FAILED);
    }

    @Test
    void mapsProviderFailureToTranslationFailed() {
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertError(fixture.client(), ErrorCode.AI_TRANSLATION_FAILED);
    }

    @Test
    void rejectsMissingApiKeyBeforeNetworkCall() {
        RestClient.Builder builder = RestClient.builder();
        OpenAiProperties properties = properties("");
        OpenAiTranslationClient client = new OpenAiTranslationClient(
                builder.build(), properties, new ObjectMapper());
        assertError(client, ErrorCode.AI_TRANSLATION_FAILED);
    }

    private void assertError(OpenAiTranslationClient client, ErrorCode errorCode) {
        assertThatThrownBy(() -> client.translate("검토해주세요.", "ko", "en"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private TestClient client() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiTranslationClient client = new OpenAiTranslationClient(
                builder.build(), properties("test-key"), new ObjectMapper());
        return new TestClient(client, server);
    }

    private OpenAiProperties properties(String apiKey) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl("https://api.openai.com/v1");
        properties.setModel("gpt-5.6-luna");
        return properties;
    }

    private String response(String translatedContent, String nuance) {
        try {
            String output = new ObjectMapper().writeValueAsString(
                    new TranslationResult(translatedContent, nuance));
            return new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "output", java.util.List.of(java.util.Map.of(
                            "content", java.util.List.of(java.util.Map.of(
                                    "type", "output_text", "text", output
                            ))
                    ))
            ));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record TestClient(OpenAiTranslationClient client, MockRestServiceServer server) {
    }
}
