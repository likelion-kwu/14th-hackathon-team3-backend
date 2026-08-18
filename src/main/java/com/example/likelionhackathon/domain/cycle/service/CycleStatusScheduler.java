package com.example.likelionhackathon.domain.cycle.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 날짜가 지난 사이클의 상태를 기간에 맞춰 넘겨준다.
 *
 * <p>디자인에는 사이클을 시작·완료시키는 버튼이 없다. 타임라인의 완료 · 진행 중 · 예정은 표시 전용이라,
 * 아무도 상태 API 를 부르지 않으면 첫날 만든 사이클이 1년 뒤에도 그대로 '진행 중' 이다.
 * 매일 한 번 기간을 보고 따라잡아서 디자인의 타임라인처럼 보이게 한다.</p>
 *
 * <p>사람이 {@code PUT /cycles/&#123;cycleId&#125;/status} 로 직접 바꾼 상태는 되돌리지 않는다.
 * {@link Cycle#catchUpTo(LocalDate)} 가 앞으로만 움직인다.</p>
 *
 * <p>사이클이 완료로 넘어가면 끝내지 못한 이슈는 다음 사이클로 옮긴다.
 * 그러지 않으면 수정도 막힌 완료 사이클 안에 이슈가 갇힌다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CycleStatusScheduler {

    private final CycleRepository cycleRepository;
    private final CycleIssuePort cycleIssuePort;

    /** 매일 0시 5분. 날짜가 바뀌자마자 돌리면 자정 근처 요청과 겹쳐 보여서 조금 미룬다. */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void catchUpStatuses() {
        LocalDate today = LocalDate.now();
        List<Cycle> candidates =
                cycleRepository.findByStatusNotAndStartDateLessThanEqual(CycleStatus.COMPLETED, today);

        int changed = 0;
        int movedIssues = 0;
        for (Cycle cycle : candidates) {
            if (!cycle.catchUpTo(today)) {
                continue;
            }
            changed++;
            if (cycle.isCompleted()) {
                movedIssues += handOverUnfinishedIssues(cycle, today);
            }
        }

        if (changed > 0) {
            log.info("사이클 {}건의 상태를 기간에 맞춰 넘기고 미완료 이슈 {}건을 이관했다. (기준일 {})",
                    changed, movedIssues, today);
        }
    }

    /**
     * 끝난 사이클의 미완료 이슈를 다음 사이클로 옮긴다.
     *
     * <p>넘길 사이클이 없으면 그대로 둔다. 마지막 사이클이라 갈 곳이 없다는 뜻이라,
     * 억지로 새 사이클을 만들기보다 사람이 프로젝트 기간을 늘리는 편이 맞다.</p>
     */
    private int handOverUnfinishedIssues(Cycle completed, LocalDate today) {
        Optional<Cycle> next = cycleRepository
                .findFirstByProjectIdAndStartDateGreaterThanAndEndDateGreaterThanEqualOrderByStartDateAsc(
                        completed.getProjectId(), completed.getStartDate(), today);

        if (next.isEmpty()) {
            log.info("사이클 {} 이 끝났지만 넘길 다음 사이클이 없어 미완료 이슈를 그대로 뒀다.", completed.getId());
            return 0;
        }

        return cycleIssuePort.moveUnfinishedIssues(completed.getId(), next.get().getId());
    }
}
