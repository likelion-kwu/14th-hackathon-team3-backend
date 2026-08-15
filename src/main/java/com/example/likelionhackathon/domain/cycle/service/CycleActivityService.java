package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.dto.CycleResponse;
import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.ActivityType;
import com.example.likelionhackathon.domain.cycle.repository.CycleActivityRepository;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.project.service.ProjectAccessService;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CycleActivityService {

    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final int MAX_PAGE_SIZE = 100;

    private final CycleActivityRepository cycleActivityRepository;
    private final CycleRepository cycleRepository;
    private final ProjectAccessService projectAccessService;

    public List<CycleResponse.ActivityGroup> getActivities(Long cycleId, String type, int page, int size) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND));
        projectAccessService.findProject(cycle.getProjectId());
        projectAccessService.requireAccess(cycle.getProjectId());

        Pageable pageable = PageRequest.of(page, validatePaging(page, size));
        ActivityType activityType = parseType(type);

        List<CycleActivity> activities = (activityType == null)
                ? cycleActivityRepository.findByCycleIdOrderByOccurredAtDescIdDesc(cycleId, pageable)
                : cycleActivityRepository.findByCycleIdAndTypeOrderByOccurredAtDescIdDesc(cycleId, activityType, pageable);

        return groupByDate(activities);
    }

    /**
     * 이슈 도메인(#8)이나 AI 분석에서 활동 한 줄을 남길 때 사용한다.
     */
    @Transactional
    public void record(CycleActivity activity) {
        cycleActivityRepository.save(activity);
    }

    private List<CycleResponse.ActivityGroup> groupByDate(List<CycleActivity> activities) {
        // 조회 결과가 이미 최신순이므로 LinkedHashMap 으로 날짜 순서를 유지한다.
        Map<LocalDate, List<CycleResponse.Activity>> grouped = new LinkedHashMap<>();
        for (CycleActivity activity : activities) {
            grouped.computeIfAbsent(activity.getOccurredAt().toLocalDate(), key -> new ArrayList<>())
                    .add(CycleResponse.Activity.of(activity));
        }

        return grouped.entrySet().stream()
                .map(entry -> new CycleResponse.ActivityGroup(
                        entry.getKey(),
                        toDateLabel(entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    private String toDateLabel(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isEqual(today)) {
            return "오늘";
        }
        if (date.isEqual(today.minusDays(1))) {
            return "어제";
        }
        return date.format(DATE_LABEL_FORMAT);
    }

    /**
     * PageRequest.of 는 잘못된 값에 IllegalArgumentException 을 던져 500 으로 나간다.
     * 400 으로 응답하도록 먼저 확인한다.
     */
    private int validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.CYCLE_INVALID_INPUT, "페이지 번호 또는 크기가 올바르지 않습니다.");
        }
        return size;
    }

    private ActivityType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ActivityType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.CYCLE_INVALID_INPUT, "지원하지 않는 활동 유형입니다.");
        }
    }
}
