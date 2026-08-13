package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 사이클이 요구하는 이슈 집계를 실제 테이블에서 계산한다.
 *
 * <p>취소된 이슈는 진행률 분모에서 뺀다. 취소한 업무 때문에 사이클 진행률이 낮아 보이면 안 되기 때문이다.
 * 취소 개수는 {@code canceledCount} 로 따로 응답한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaCycleIssuePort implements CycleIssuePort {

    private static final List<IssueStatus> UNFINISHED_EXCLUDED =
            List.of(IssueStatus.DONE, IssueStatus.CANCELED);

    private final IssueRepository issueRepository;

    @Override
    public IssueStats statsOf(Long cycleId) {
        Map<IssueStatus, Long> counts = new EnumMap<>(IssueStatus.class);
        issueRepository.countGroupByStatus(cycleId)
                .forEach(row -> counts.put(row.getStatus(), row.getCount()));

        int canceledCount = countOf(counts, IssueStatus.CANCELED);
        int totalCount = counts.values().stream().mapToInt(Long::intValue).sum() - canceledCount;

        return new IssueStats(
                totalCount,
                countOf(counts, IssueStatus.DONE),
                countOf(counts, IssueStatus.IN_PROGRESS),
                countOf(counts, IssueStatus.NEEDS_REVIEW),
                canceledCount
        );
    }

    @Override
    public boolean hasAnyIssue(Long cycleId) {
        return issueRepository.existsByCycleId(cycleId);
    }

    @Override
    @Transactional
    public int moveUnfinishedIssues(Long fromCycleId, Long toCycleId) {
        List<Issue> unfinished = issueRepository.findByCycleIdAndStatusNotIn(fromCycleId, UNFINISHED_EXCLUDED);
        unfinished.forEach(issue -> issue.moveToCycle(toCycleId));
        return unfinished.size();
    }

    private int countOf(Map<IssueStatus, Long> counts, IssueStatus status) {
        return counts.getOrDefault(status, 0L).intValue();
    }
}
