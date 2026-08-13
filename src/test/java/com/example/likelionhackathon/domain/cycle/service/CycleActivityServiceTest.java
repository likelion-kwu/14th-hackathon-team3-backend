package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.dto.CycleResponse;
import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.ActivityType;
import com.example.likelionhackathon.domain.cycle.repository.CycleActivityRepository;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleActivityServiceTest {

    private static final Long CYCLE_ID = 3L;

    @Mock
    private CycleActivityRepository cycleActivityRepository;

    @Mock
    private CycleRepository cycleRepository;

    private CycleActivityService cycleActivityService;

    @BeforeEach
    void setUp() {
        cycleActivityService = new CycleActivityService(cycleActivityRepository, cycleRepository);
    }

    @Test
    void rejectsUnsupportedActivityType() {
        when(cycleRepository.existsById(CYCLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> cycleActivityService.getActivities(CYCLE_ID, "SOMETHING_ELSE", 0, 20))
                .isInstanceOf(CustomException.class)
                .hasMessage("지원하지 않는 활동 유형입니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_INVALID_INPUT);
    }

    @Test
    void returns404WhenCycleMissing() {
        when(cycleRepository.existsById(CYCLE_ID)).thenReturn(false);

        assertThatThrownBy(() -> cycleActivityService.getActivities(CYCLE_ID, null, 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CYCLE_NOT_FOUND);
    }

    @Test
    void groupsActivitiesByDateWithRelativeLabel() {
        LocalDateTime todayAfternoon = LocalDateTime.of(java.time.LocalDate.now(), LocalTime.of(14, 32));
        LocalDateTime yesterdayMorning =
                LocalDateTime.of(java.time.LocalDate.now().minusDays(1), LocalTime.of(11, 3));

        when(cycleRepository.existsById(CYCLE_ID)).thenReturn(true);
        when(cycleActivityRepository.findByCycleIdOrderByOccurredAtDesc(any(), any())).thenReturn(List.of(
                CycleActivity.issueStatusChanged(
                        CYCLE_ID, todayAfternoon, "김민준", 301L, "결제 API v3 연동", "IN_PROGRESS", "DONE"),
                CycleActivity.aiProgressUpdated(CYCLE_ID, todayAfternoon.minusMinutes(22), 72, 78, "갱신"),
                CycleActivity.fileUploaded(
                        CYCLE_ID, yesterdayMorning, "김서연", 305L, "QA_Result_v2.pdf", 2516582L)
        ));

        List<CycleResponse.ActivityGroup> groups =
                cycleActivityService.getActivities(CYCLE_ID, null, 0, 20);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).dateLabel()).isEqualTo("오늘");
        assertThat(groups.get(0).activities()).hasSize(2);
        assertThat(groups.get(0).activities().get(0).type()).isEqualTo(ActivityType.ISSUE_STATUS_CHANGED);
        assertThat(groups.get(0).activities().get(0).before()).isEqualTo("IN_PROGRESS");
        assertThat(groups.get(0).activities().get(0).after()).isEqualTo("DONE");
        assertThat(groups.get(1).dateLabel()).isEqualTo("어제");
        assertThat(groups.get(1).activities().get(0).fileName()).isEqualTo("QA_Result_v2.pdf");
    }
}
