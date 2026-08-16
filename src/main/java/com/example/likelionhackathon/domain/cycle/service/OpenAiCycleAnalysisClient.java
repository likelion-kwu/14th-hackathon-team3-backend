package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CheckNeededType;
import com.example.likelionhackathon.global.config.OpenAiProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사이클 상황을 OpenAI 로 정리한다.
 *
 * <p>접속 설정({@code openAiRestClient} 빈)만 인수인계 도메인과 공유하고,
 * 넘기는 데이터와 프롬프트는 사이클 것만 쓴다.</p>
 *
 * <p>어떤 이유로든 실패하면 비워서 돌려준다. 분석이 안 됐다고 화면이 비면 안 되고,
 * 부르는 쪽이 집계 문장으로 대신하기 때문이다.</p>
 */
@Slf4j
@Service
public class OpenAiCycleAnalysisClient implements CycleAnalysisPort {

    /** 프롬프트가 지나치게 길어지지 않도록 넘기는 개수를 제한한다. */
    private static final int MAX_ISSUES = 60;
    private static final int MAX_ACTIVITIES = 40;
    private static final int MAX_OUTPUT_TOKENS = 1_200;

    private static final String INSTRUCTIONS = """
            당신은 팀 협업 도구에서 한 사이클(스프린트)의 진행 상황을 정리하는 도우미입니다.
            입력으로 준 이슈와 활동 기록만 근거로 사용하고, 없는 사실을 지어내지 마세요.

            summary 규칙:
            - 한국어 2~3문장. 숫자를 나열하지 말고 상황을 판단해 주세요.
            - 계획 진행률(plannedProgressRate)과 실제 진행률(progressRate)의 차이를 언급하세요.
            - 마감이 임박했는데 멈춰 있는 업무가 있으면 그것을 우선 알려주세요.

            checkNeeded 규칙:
            - 사람이 직접 확인해야 하는 이슈만 담으세요. 없으면 빈 배열로 두세요.
            - 확인 필요(NEEDS_REVIEW) 상태로 오래 머무는 이슈, 마감이 지났는데 끝나지 않은 이슈가 대상입니다.
            - message 는 왜 확인이 필요한지 한 문장으로 쓰세요.
            - 판단 근거가 모자라면 INSUFFICIENT_EVIDENCE, 답을 기다리는 상태로 보이면 UNANSWERED_QUESTION 을 쓰세요.
            - issueId 는 반드시 입력에 있는 값이어야 합니다.
            """;

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCycleAnalysisClient(
            RestClient openAiRestClient,
            OpenAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.openAiRestClient = openAiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Result> analyze(Input input) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            log.info("OpenAI API 키가 없어 사이클 AI 분석을 건너뜁니다.");
            return Optional.empty();
        }
        if (input.issues().isEmpty()) {
            return Optional.empty();
        }

        try {
            JsonNode response = openAiRestClient.post()
                    .uri("/responses")
                    .body(buildRequest(input))
                    .retrieve()
                    .body(JsonNode.class);

            return Optional.of(toResult(objectMapper.readTree(extractOutputText(response)), input));
        } catch (RestClientException | JacksonException | IllegalStateException e) {
            log.warn("사이클 AI 분석 호출에 실패해 집계 요약으로 대체합니다: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> buildRequest(Input input) throws JacksonException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        request.put("store", false);
        request.put("instructions", INSTRUCTIONS);
        request.put("input", objectMapper.writeValueAsString(toPayload(input)));
        request.put("text", Map.of("format", buildJsonSchemaFormat()));
        request.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        return request;
    }

    private Map<String, Object> toPayload(Input input) {
        Map<String, Object> cycle = new LinkedHashMap<>();
        cycle.put("name", input.cycleName());
        cycle.put("goal", input.goal());
        cycle.put("startDate", String.valueOf(input.startDate()));
        cycle.put("endDate", String.valueOf(input.endDate()));
        cycle.put("today", String.valueOf(input.today()));
        cycle.put("daysLeft", ChronoUnit.DAYS.between(input.today(), input.endDate()));
        cycle.put("progressRate", input.progressRate());
        cycle.put("plannedProgressRate", input.plannedProgressRate());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCount", input.stats().totalCount());
        stats.put("doneCount", input.stats().doneCount());
        stats.put("inProgressCount", input.stats().inProgressCount());
        stats.put("needsReviewCount", input.stats().needsReviewCount());
        stats.put("canceledCount", input.stats().canceledCount());

        List<Map<String, Object>> issues = new ArrayList<>();
        input.issues().stream().limit(MAX_ISSUES).forEach(issue -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("issueId", issue.issueId());
            row.put("title", issue.title());
            row.put("status", issue.status());
            row.put("priority", issue.priority());
            row.put("dueDate", String.valueOf(issue.dueDate()));
            row.put("checklistDone", issue.checklistDoneCount());
            row.put("checklistTotal", issue.checklistTotalCount());
            issues.add(row);
        });

        List<Map<String, Object>> activities = new ArrayList<>();
        input.activities().stream().limit(MAX_ACTIVITIES).forEach(activity -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", activity.type());
            row.put("occurredAt", String.valueOf(activity.occurredAt()));
            row.put("actorName", activity.actorName());
            row.put("issueTitle", activity.issueTitle());
            row.put("before", activity.before());
            row.put("after", activity.after());
            row.put("reason", activity.reason());
            activities.add(row);
        });

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cycle", cycle);
        payload.put("stats", stats);
        payload.put("issues", issues);
        payload.put("activities", activities);
        return payload;
    }

    private Map<String, Object> buildJsonSchemaFormat() {
        Map<String, Object> checkProperties = new LinkedHashMap<>();
        checkProperties.put("type", Map.of("type", "string", "enum", enumNames(CheckNeededType.values())));
        checkProperties.put("message", Map.of("type", "string"));
        checkProperties.put("issueId", Map.of("type", List.of("integer", "null")));

        Map<String, Object> checkSchema = new LinkedHashMap<>();
        checkSchema.put("type", "object");
        checkSchema.put("additionalProperties", false);
        checkSchema.put("properties", checkProperties);
        checkSchema.put("required", List.of("type", "message", "issueId"));

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "object");
        rootSchema.put("additionalProperties", false);
        rootSchema.put("properties", Map.of(
                "summary", Map.of("type", "string"),
                "checkNeeded", Map.of("type", "array", "items", checkSchema)
        ));
        rootSchema.put("required", List.of("summary", "checkNeeded"));

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "cycle_analysis");
        format.put("strict", true);
        format.put("schema", rootSchema);
        return format;
    }

    private Result toResult(JsonNode root, Input input) {
        String summary = root.path("summary").asText("").strip();
        if (summary.isEmpty()) {
            throw new IllegalStateException("summary가 비어 있습니다.");
        }

        List<Long> knownIssueIds = input.issues().stream().map(CycleIssuePort.IssueBrief::issueId).toList();
        List<CheckNeeded> checkNeeded = new ArrayList<>();

        for (JsonNode node : root.path("checkNeeded")) {
            String message = node.path("message").asText("").strip();
            if (message.isEmpty()) {
                continue;
            }

            Long issueId = node.path("issueId").isNumber() ? node.path("issueId").asLong() : null;
            // 모델이 없는 이슈를 지어낼 수 있어 입력에 있던 것만 남긴다.
            if (issueId != null && !knownIssueIds.contains(issueId)) {
                log.warn("AI 분석이 사이클에 없는 이슈를 가리켜 무시합니다. issueId={}", issueId);
                issueId = null;
            }

            checkNeeded.add(new CheckNeeded(parseType(node.path("type").asText()), message, issueId));
        }

        return new Result(summary, checkNeeded);
    }

    private CheckNeededType parseType(String value) {
        try {
            return CheckNeededType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return CheckNeededType.INSUFFICIENT_EVIDENCE;
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response == null || !response.path("output").isArray()) {
            throw new IllegalStateException("Responses API output이 없습니다.");
        }

        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new IllegalStateException("모델이 사이클 분석을 거부했습니다.");
                }
                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IllegalStateException("Responses API의 output_text가 없습니다.");
    }

    private List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
