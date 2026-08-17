package com.example.likelionhackathon.domain.conversation.service;

import com.example.likelionhackathon.domain.conversation.service.TemporalModels.TemporalExtraction;
import com.example.likelionhackathon.domain.conversation.service.TemporalModels.TemporalExpression;
import com.example.likelionhackathon.global.config.OpenAiProperties;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiTemporalExpressionClient {
    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public TemporalExtraction extract(String content, String sourceLanguage) {
        if (!StringUtils.hasText(properties.getApiKey()))
            throw new CustomException(ErrorCode.AI_TRANSLATION_FAILED, "OpenAI API 키가 설정되지 않았습니다.");
        try {
            JsonNode response = openAiRestClient.post().uri("/responses")
                    .body(buildRequest(content, sourceLanguage)).retrieve().body(JsonNode.class);
            TemporalExtraction extraction = objectMapper.readValue(extractOutputText(response), TemporalExtraction.class);
            validate(extraction);
            return extraction;
        } catch (CustomException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            log.warn("OpenAI temporal extraction timeout: {}", exception.getMessage());
            throw new CustomException(ErrorCode.AI_TRANSLATION_TIMEOUT);
        } catch (RestClientResponseException exception) {
            log.warn("OpenAI temporal extraction provider failure: status={}", exception.getStatusCode());
            throw new CustomException(ErrorCode.AI_TRANSLATION_FAILED);
        } catch (JacksonException | IllegalStateException | IllegalArgumentException | RestClientException exception) {
            log.warn("OpenAI temporal extraction response failure: {}", exception.getMessage());
            throw new CustomException(ErrorCode.AI_TRANSLATION_FAILED);
        }
    }

    private Map<String, Object> buildRequest(String content, String sourceLanguage) throws JacksonException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        request.put("store", false);
        request.put("instructions", """
                Extract only natural-language date/time meaning from the message. Do not translate it.
                Identify every expression and its exact original text, relative-date meaning, optional ISO local time,
                optional ISO explicit date, weekday, and semantic role. Use uppercase English weekday names.
                Relative-date rules:
                - Korean: 오늘/오늘까지/오늘부터 = TODAY; 내일/내일까지/내일부터 = TOMORROW;
                  모레/모레까지/모레부터 = DAY_AFTER_TOMORROW.
                  까지 means a deadline (by/until) and 부터 means a starting point; neither advances the date.
                  In particular, 내일까지 is always TOMORROW, never DAY_AFTER_TOMORROW.
                - English: today = TODAY; tomorrow/by tomorrow = TOMORROW;
                  day after tomorrow = DAY_AFTER_TOMORROW. "by" never advances the date.
                - Japanese: 今日 = TODAY; 明日/明日まで = TOMORROW; 明後日/明後日まで = DAY_AFTER_TOMORROW.
                  まで never advances the date.
                Compact semantic examples:
                1) "내일까지 확인해주세요." -> originalText="내일까지", type=RELATIVE_DATE,
                   relativeDateType=TOMORROW, localTime=null, hasExplicitTime=false, role=DEADLINE.
                2) "모레까지 확인해주세요." -> originalText="모레까지", type=RELATIVE_DATE,
                   relativeDateType=DAY_AFTER_TOMORROW, localTime=null, hasExplicitTime=false, role=DEADLINE.
                3) "내일 오후 3시까지 확인해주세요." -> originalText="내일 오후 3시까지",
                   type=RELATIVE_DATE_TIME, relativeDateType=TOMORROW, localTime="15:00",
                   hasExplicitTime=true, role=DEADLINE.
                Never calculate a calendar date, timezone, UTC offset, DST, or recipient local time.
                Those calculations belong exclusively to the backend TemporalResolver.
                Never invent a time that is not explicit. If there is no real temporal expression, return false and [].
                """);
        request.put("input", objectMapper.writeValueAsString(Map.of(
                "sourceLanguage", sourceLanguage, "content", content)));
        request.put("text", Map.of("format", schemaFormat()));
        request.put("max_output_tokens", 2_500);
        return request;
    }

    private Map<String, Object> schemaFormat() {
        Map<String, Object> expressionProperties = new LinkedHashMap<>();
        expressionProperties.put("originalText", Map.of("type", "string"));
        expressionProperties.put("type", enumSchema("RELATIVE_DATE", "RELATIVE_DATE_TIME", "WEEKDAY", "EXPLICIT_DATE", "EXPLICIT_DATE_TIME"));
        expressionProperties.put("relativeDateType", enumSchema("TODAY", "TOMORROW", "DAY_AFTER_TOMORROW", "NEXT_WEEK", "NEXT_DAY_OF_WEEK", "NONE"));
        expressionProperties.put("dayOfWeek", nullableString());
        expressionProperties.put("explicitDate", nullableString());
        expressionProperties.put("localTime", nullableString());
        expressionProperties.put("hasExplicitTime", Map.of("type", "boolean"));
        expressionProperties.put("role", enumSchema("DEADLINE", "EVENT_TIME", "REFERENCE"));
        Map<String, Object> item = Map.of("type", "object", "additionalProperties", false,
                "properties", expressionProperties, "required", new ArrayList<>(expressionProperties.keySet()));
        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("hasTemporalExpression", Map.of("type", "boolean"));
        rootProperties.put("expressions", Map.of("type", "array", "items", item));
        Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false,
                "properties", rootProperties, "required", List.of("hasTemporalExpression", "expressions"));
        return Map.of("type", "json_schema", "name", "temporal_expression_extraction", "strict", true, "schema", schema);
    }

    private Map<String, Object> enumSchema(String... values) { return Map.of("type", "string", "enum", List.of(values)); }
    private Map<String, Object> nullableString() { return Map.of("type", List.of("string", "null")); }

    private String extractOutputText(JsonNode response) {
        if (response == null || !response.path("output").isArray()) throw new IllegalStateException("Responses API output is missing");
        for (JsonNode output : response.path("output")) for (JsonNode content : output.path("content")) {
            if ("refusal".equals(content.path("type").asText())) throw new IllegalStateException("Model refusal");
            if ("output_text".equals(content.path("type").asText())) return content.path("text").asText();
        }
        throw new IllegalStateException("Responses API output_text is missing");
    }

    private void validate(TemporalExtraction extraction) {
        if (extraction == null || extraction.expressions() == null
                || extraction.hasTemporalExpression() != !extraction.expressions().isEmpty())
            throw new IllegalArgumentException("Inconsistent temporal extraction");
        for (TemporalExpression expression : extraction.expressions()) {
            if (!StringUtils.hasText(expression.originalText()) || expression.type() == null || expression.role() == null
                    || expression.relativeDateType() == null
                    || (expression.hasExplicitTime() && !StringUtils.hasText(expression.localTime())))
                throw new IllegalArgumentException("Invalid temporal expression");
        }
    }
}
