package com.example.likelionhackathon.domain.handover.service;

import com.example.likelionhackathon.domain.handover.dto.OpenAiHandoverResult;
import com.example.likelionhackathon.domain.handover.entity.CollaborationActivity;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ItemCategory;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;
import com.example.likelionhackathon.global.config.OpenAiProperties;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiHandoverClient {

    private static final int MAX_ACTIVITY_CONTENT_LENGTH = 2_000;

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiHandoverResult generate(List<CollaborationActivity> activities) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new CustomException(ErrorCode.SOURCE_SYNC_FAILED, "OpenAI API 키가 설정되지 않았습니다.");
        }

        try {
            JsonNode response = openAiRestClient.post()
                    .uri("/responses")
                    .body(buildRequest(activities))
                    .retrieve()
                    .body(JsonNode.class);

            String outputText = extractOutputText(response);
            OpenAiHandoverResult result = objectMapper.readValue(outputText, OpenAiHandoverResult.class);
            if (result.items() == null) {
                throw new CustomException(ErrorCode.SOURCE_SYNC_FAILED, "AI 응답에 인수인계 항목이 없습니다.");
            }
            return result;
        } catch (CustomException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("OpenAI Responses API 호출 실패: status={}", e.getStatusCode());
            throw new CustomException(ErrorCode.SOURCE_SYNC_FAILED, "AI 인수인계 생성에 실패했습니다.");
        } catch (JacksonException | IllegalStateException e) {
            log.warn("OpenAI 응답 파싱 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SOURCE_SYNC_FAILED, "AI 인수인계 응답을 처리할 수 없습니다.");
        }
    }

    private Map<String, Object> buildRequest(List<CollaborationActivity> activities) throws JacksonException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        request.put("store", false);
        request.put("instructions", """
                당신은 글로벌 협업 프로젝트의 인수인계 초안을 작성하는 도우미입니다.
                입력으로 제공된 활동만 근거로 사용하고 사실을 추측하지 마세요.
                업무를 COMPLETED, IN_PROGRESS, NEXT_ACTION, DECISION, QUESTION 중 하나로 분류하세요.
                충분한 근거가 있으면 VERIFIED, 확인이 필요하면 NEEDS_REVIEW,
                답이 없는 질문이면 UNANSWERED로 표시하세요. 결과는 한국어로 작성하세요.
                evidenceIndexes에는 각 항목을 뒷받침하는 입력 활동의 index만 넣으세요.
                """);
        request.put("input", objectMapper.writeValueAsString(toActivityInputs(activities)));
        request.put("text", Map.of("format", buildJsonSchemaFormat()));
        request.put("max_output_tokens", 4_000);
        return request;
    }

    private List<Map<String, Object>> toActivityInputs(List<CollaborationActivity> activities) {
        List<Map<String, Object>> inputs = new ArrayList<>();
        for (int index = 0; index < activities.size(); index++) {
            CollaborationActivity activity = activities.get(index);
            inputs.add(Map.of(
                    "index", index,
                    "provider", activity.getProvider().name(),
                    "sourceName", activity.getSourceName(),
                    "content", truncate(activity.getContent(), MAX_ACTIVITY_CONTENT_LENGTH),
                    "occurredAt", activity.getOccurredAt().toString()
            ));
        }
        return inputs;
    }

    private Map<String, Object> buildJsonSchemaFormat() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("category", Map.of(
                "type", "string",
                "enum", enumNames(ItemCategory.values())
        ));
        itemProperties.put("title", Map.of("type", "string"));
        itemProperties.put("description", Map.of("type", "string"));
        itemProperties.put("assigneeMemberId", Map.of("type", List.of("integer", "null")));
        itemProperties.put("reviewStatus", Map.of(
                "type", "string",
                "enum", enumNames(ReviewStatus.values())
        ));
        itemProperties.put("evidenceIndexes", Map.of(
                "type", "array",
                "items", Map.of("type", "integer")
        ));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("properties", itemProperties);
        itemSchema.put("required", List.of(
                "category", "title", "description", "assigneeMemberId", "reviewStatus", "evidenceIndexes"
        ));

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "object");
        rootSchema.put("additionalProperties", false);
        rootSchema.put("properties", Map.of(
                "items", Map.of("type", "array", "items", itemSchema)
        ));
        rootSchema.put("required", List.of("items"));

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "handover_draft");
        format.put("strict", true);
        format.put("schema", rootSchema);
        return format;
    }

    private String extractOutputText(JsonNode response) {
        if (response == null || !response.path("output").isArray()) {
            throw new IllegalStateException("Responses API output이 없습니다.");
        }

        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new IllegalStateException("모델이 인수인계 생성을 거부했습니다.");
                }
                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IllegalStateException("Responses API의 output_text가 없습니다.");
    }

    private List<String> enumNames(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
