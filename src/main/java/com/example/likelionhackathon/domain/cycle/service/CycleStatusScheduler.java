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

/**
 * 날짜가 지난 사이클의 상태를 기간에 맞춰 넘겨준다.
 *
 * <p>디자인에는 사이클을 시작·완료시키는 버튼이 없다. 타임라인의 완료 · 진행 중 · 예정은 표시 전용이라,
 * 아무도 상태 API 를 부르지 않으면 첫날 만든 사이클이 1년 뒤에도 그대로 '진행 중' 이다.
 * 매일 한 번 기간을 보고 따라잡아서 디자인의 타임라인처럼 보이게 한다.</p>
 *
 * <p>사람이 {@code PUT /cycles/&#123;cycleId&#125;/status} 로 직접 바꾼 상태는 되돌리지 않는다.
 * {@link Cycle#catchUpTo(LocalDate)} 가 앞으로만 움직인다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CycleStatusScheduler {

    private final CycleRepository cycleRepository;

    /** 매일 0시 5분. 날짜가 바뀌자마자 돌리면 자정 근처 요청과 겹쳐 보여서 조금 미룬다. */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void catchUpStatuses() {
        LocalDate today = LocalDate.now();
        List<Cycle> candidates =
                cycleRepository.findByStatusNotAndStartDateLessThanEqual(CycleStatus.COMPLETED, today);

        long changed = candidates.stream()
                .filter(cycle -> cycle.catchUpTo(today))
                .count();

        if (changed > 0) {
            log.info("사이클 {}건의 상태를 기간에 맞춰 넘겼다. (기준일 {})", changed, today);
        }
    }
}
