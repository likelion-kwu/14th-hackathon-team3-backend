package com.example.likelionhackathon.domain.handover.service;

import com.example.likelionhackathon.domain.handover.dto.OpenAiHandoverResult;
import com.example.likelionhackathon.domain.handover.entity.CollaborationActivity;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ItemCategory;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.Provider;
import com.example.likelionhackathon.global.config.OpenAiProperties;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiHandoverClientTest {

    @Test
    void parsesStructuredResponsesApiOutput() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProperties properties = properties("test-key");
        OpenAiHandoverClient client = new OpenAiHandoverClient(
                builder.build(),
                properties,
                new ObjectMapper()
        );

        server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "type": "message",
                              "content": [
                                {
                                  "type": "output_text",
                                  "text": "{\\\"items\\\":[{\\\"category\\\":\\\"IN_PROGRESS\\\",\\\"title\\\":\\\"결제 API 테스트\\\",\\\"description\\\":\\\"운영 환경 테스트가 필요합니다.\\\",\\\"assigneeMemberId\\\":7,\\\"reviewStatus\\\":\\\"VERIFIED\\\",\\\"evidenceIndexes\\\":[0]}]}"
                                }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        OpenAiHandoverResult result = client.generate(List.of(activity()));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().category()).isEqualTo(ItemCategory.IN_PROGRESS);
        assertThat(result.items().getFirst().evidenceIndexes()).containsExactly(0);
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyBeforeNetworkCall() {
        OpenAiHandoverClient client = new OpenAiHandoverClient(
                RestClient.builder().build(),
                properties(""),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> client.generate(List.of(activity())))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOURCE_SYNC_FAILED);
    }

    private OpenAiProperties properties(String apiKey) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl("https://api.openai.com/v1");
        properties.setModel("gpt-5.6-luna");
        return properties;
    }

    private CollaborationActivity activity() {
        return new CollaborationActivity(
                1L,
                2L,
                Provider.SLACK,
                "#general",
                "결제 API 운영 환경 테스트가 남아 있습니다.",
                "https://slack.com/example",
                OffsetDateTime.parse("2026-08-10T12:00:00Z")
        );
    }
}
