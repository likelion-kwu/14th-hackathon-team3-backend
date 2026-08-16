package com.example.likelionhackathon.domain.conversation.service;

import com.example.likelionhackathon.global.config.OpenAiProperties;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.likelionhackathon.domain.conversation.service.TemporalModels.ResolvedTemporalContext;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiTranslationClient {

    private static final int MAX_TRANSLATED_CONTENT_LENGTH = 4_000;

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public TranslationResult translate(String content, String sourceLanguage, String targetLanguage) {
        return translate(content, sourceLanguage, targetLanguage, null);
    }

    public TranslationResult translate(String content, String sourceLanguage, String targetLanguage,
                                       ResolvedTemporalContext temporalContext) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new CustomException(ErrorCode.AI_TRANSLATION_FAILED, "OpenAI API 키가 설정되지 않았습니다.");
        }

        try {
            JsonNode response = openAiRestClient.post()
                    .uri("/responses")
                    .body(buildRequest(content, sourceLanguage, targetLanguage, temporalContext))
                    .retrieve()
                    .body(JsonNode.class);
            TranslationResult result = objectMapper.readValue(extractOutputText(response), TranslationResult.class);
            validateResult(result);
            return result;
        } catch (CustomException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            log.warn("OpenAI translation timeout or network access failure: {}", exception.getMessage());
            throw new CustomException(ErrorCode.AI_TRANSLATION_TIMEOUT);
        } catch (RestClientResponseException exception) {
            log.warn("OpenAI translation provider failure: status={}", exception.getStatusCode());
            throw new CustomException(ErrorCode.AI_TRANSLATION_FAILED);
        } catch (JacksonException | IllegalStateException | RestClientException exception) {
            log.warn("OpenAI translation response failure: {}", exception.getMessage());
            throw new CustomException(ErrorCode.AI_TRANSLATION_FAILED);
        }
    }

    private Map<String, Object> buildRequest(
            String content, String sourceLanguage, String targetLanguage,
            ResolvedTemporalContext temporalContext) throws JacksonException {
        String sourceLanguageName = languageName(sourceLanguage);
        String targetLanguageName = languageName(targetLanguage);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        request.put("store", false);
        request.put("instructions", """
                You translate workplace messages so their intent and strength sound natural in the target business culture.
                Translate from the explicitly supplied sourceLanguage to targetLanguage.
                Follow this priority: (1) preserve core meaning; (2) preserve deadlines, urgency, and request strength;
                (3) preserve dates, times, numbers, names, and facts; (4) soften unnecessary blame or aggression into
                professional workplace wording; (5) use natural business language; (6) add no apology, reason,
                promise, solution, emotion, task, or fact absent from the source.
                Tone adjustment is selective, not automatic. Keep ordinary messages such as greetings concise.
                Do not weaken firm urgency into optional language. Do not make concise wording excessively formal or verbose.
                Preserve people, companies, projects, technical terms, URLs, code, and file names exactly.
                Tone examples (style guidance, not text to copy):
                - "왜 아직 안됐나요? 내일까지 꼭 처리해주세요." Avoid "Why hasn't this been done yet?";
                  prefer a professional status/cause inquiry such as "Could you let me know what's causing the delay?"
                  while retaining a firm request to complete it by the backend-specified deadline.
                - "이거 잘못된 것 같은데 다시 확인해주세요." ->
                  "There may be an issue with this. Could you please check it again?"
                - "빨리 보내주세요." -> "Could you please send this as soon as possible?"
                Return translatedContent only as the message the recipient should read.
                The backend-resolved dates/times, when supplied, are authoritative and final. Never recalculate,
                timezone-convert, increment, decrement, or otherwise alter them, and never revert them to relative wording.
                For hasExplicitTime=false, senderDate is the final absolute date to express exactly as supplied.
                Do not apply receiverZoneId to senderDate and never invent a time (including 00:00 or 23:59).
                For hasExplicitTime=true, receiverDateTime is the recipient's final local date/time; express that exact
                calendar date and clock time without doing any additional timezone or DST calculation.
                Never change names, numbers, dates, or times. The output message MUST be in targetLanguage.
                The nuance MUST be written only in nuanceLanguage, never in another language.
                Nuance must briefly and accurately describe any preserved strength or tone adjustment actually visible
                in translatedContent; never claim an adjustment that the translated message did not make.
                """);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sourceLanguage", Map.of("code", sourceLanguage, "name", sourceLanguageName));
        input.put("targetLanguage", Map.of("code", targetLanguage, "name", targetLanguageName));
        input.put("nuanceLanguage", Map.of("code", sourceLanguage, "name", sourceLanguageName));
        input.put("content", content);
        if (temporalContext != null) input.put("resolvedTemporalContext", temporalContext);
        request.put("input", objectMapper.writeValueAsString(input));
        request.put("text", Map.of("format", buildJsonSchemaFormat()));
        request.put("max_output_tokens", 4_500);
        return request;
    }

    private String languageName(String language) {
        return switch (language) {
            case "ko" -> "Korean";
            case "en" -> "English";
            case "ja" -> "Japanese";
            default -> throw new CustomException(ErrorCode.TRANSLATION_LANGUAGE_NOT_CONFIGURED);
        };
    }

    private Map<String, Object> buildJsonSchemaFormat() {
        Map<String, Object> propertiesSchema = new LinkedHashMap<>();
        propertiesSchema.put("translatedContent", Map.of(
                "type", "string",
                "minLength", 1,
                "maxLength", MAX_TRANSLATED_CONTENT_LENGTH
        ));
        propertiesSchema.put("nuance", Map.of("type", "string", "minLength", 1));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", propertiesSchema);
        schema.put("required", List.of("translatedContent", "nuance"));

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "workplace_message_translation");
        format.put("strict", true);
        format.put("schema", schema);
        return format;
    }

    private String extractOutputText(JsonNode response) {
        if (response == null || !response.path("output").isArray()) {
            throw new IllegalStateException("Responses API output is missing.");
        }
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new IllegalStateException("The model refused the translation request.");
                }
                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IllegalStateException("Responses API output_text is missing.");
    }

    private void validateResult(TranslationResult result) {
        if (result == null
                || !StringUtils.hasText(result.translatedContent())
                || result.translatedContent().length() > MAX_TRANSLATED_CONTENT_LENGTH
                || !StringUtils.hasText(result.nuance())) {
            throw new CustomException(ErrorCode.AI_TRANSLATION_FAILED);
        }
    }

    public record TranslationResult(String translatedContent, String nuance) {
    }
}
