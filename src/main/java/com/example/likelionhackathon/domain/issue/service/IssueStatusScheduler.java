package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 마감일이 지나도록 끝나지 않은 이슈를 지연으로 표시한다.
 *
 * <p>디자인의 주요 진행 상황에는 '지연됨' 배지가 있는데, 이슈를 지연으로 바꾸는 화면은 없다.
 * 아무도 상태 API 를 부르지 않으면 마감일이 한참 지난 이슈도 계속 '할 일' 로 남아
 * 그 배지가 영영 나오지 않는다.</p>
 *
 * <p>사이클 상태를 넘기는 {@code CycleStatusScheduler} 뒤에 돈다. 앞뒤가 바뀌어도 결과는 같지만,
 * 이관된 이슈까지 같은 날 지연으로 잡히는 편이 화면에서 덜 헷갈린다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueStatusScheduler {

    /** 지연으로 넘길 대상. 확인 필요는 사람의 답을 기다리는 상태라 뺀다. */
    private static final List<IssueStatus> DELAYABLE = List.of(IssueStatus.TODO, IssueStatus.IN_PROGRESS);

    private final IssueRepository issueRepository;

    @Scheduled(cron = "0 10 0 * * *")
    @Transactional
    public void markOverdueIssuesDelayed() {
        LocalDate today = LocalDate.now();
        List<Issue> overdue = issueRepository.findByStatusInAndDueDateBefore(DELAYABLE, today);

        long delayed = overdue.stream()
                .filter(issue -> issue.markDelayedIfOverdue(today))
                .count();

        if (delayed > 0) {
            log.info("마감일이 지난 이슈 {}건을 지연으로 표시했다. (기준일 {})", delayed, today);
        }
    }
}
