package com.example.likelionhackathon.domain.conversation;

import com.example.likelionhackathon.domain.conversation.service.OpenAiTemporalExpressionClient;
import com.example.likelionhackathon.domain.conversation.service.TemporalModels.*;
import com.example.likelionhackathon.global.config.OpenAiProperties;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiTemporalExpressionClientTest {
    @Test void parsesTomorrowAndMultipleExpressionsUsingStrictSchema() throws Exception {
        Fixture fixture = fixture();
        var expressions = List.of(
                expression("내일", false, null),
                new TemporalExpression("오후 3시", Type.RELATIVE_DATE_TIME, RelativeDateType.TOMORROW,
                        null, null, "15:00", true, Role.EVENT_TIME));
        fixture.server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("additionalProperties")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sourceLanguage")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("내일까지 is always TOMORROW")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("까지 means a deadline")))
                .andRespond(withSuccess(response(new TemporalExtraction(true, expressions)), MediaType.APPLICATION_JSON));
        TemporalExtraction result = fixture.client.extract("내일 오후 3시", "ko");
        assertThat(result.expressions()).hasSize(2);
        assertThat(result.expressions().get(1).localTime()).isEqualTo("15:00");
    }

    @Test void rejectsMalformedAndInconsistentOutput() throws Exception {
        Fixture malformed = fixture();
        malformed.server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(output("not-json"), MediaType.APPLICATION_JSON));
        assertFailed(malformed.client);
        Fixture inconsistent = fixture();
        inconsistent.server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(response(new TemporalExtraction(false, List.of(expression("내일", false, null)))), MediaType.APPLICATION_JSON));
        assertFailed(inconsistent.client);
    }

    @Test void mapsProviderFailure() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertFailed(fixture.client);
    }

    private void assertFailed(OpenAiTemporalExpressionClient client) {
        assertThatThrownBy(() -> client.extract("내일", "ko")).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_TRANSLATION_FAILED);
    }
    private TemporalExpression expression(String text, boolean hasTime, String time) {
        return new TemporalExpression(text, hasTime ? Type.RELATIVE_DATE_TIME : Type.RELATIVE_DATE,
                RelativeDateType.TOMORROW, null, null, time, hasTime, Role.DEADLINE);
    }
    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProperties properties = new OpenAiProperties(); properties.setApiKey("test-key");
        return new Fixture(new OpenAiTemporalExpressionClient(builder.build(), properties, new ObjectMapper()), server);
    }
    private String response(TemporalExtraction extraction) throws Exception {
        return output(new ObjectMapper().writeValueAsString(extraction));
    }
    private String output(String text) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("output", List.of(Map.of("content",
                List.of(Map.of("type", "output_text", "text", text))))));
    }
    private record Fixture(OpenAiTemporalExpressionClient client, MockRestServiceServer server) {}
}
