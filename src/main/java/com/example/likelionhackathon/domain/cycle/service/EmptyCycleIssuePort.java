package com.example.likelionhackathon.domain.cycle.service;

import org.springframework.stereotype.Service;

/**
 * 이슈 도메인이 아직 없는 동안 쓰는 임시 구현.
 *
 * <p>⚠️ 이슈 도메인(#8) 구현 시 이 클래스를 삭제하고 JPA 기반 구현체로 교체한다.
 * 삭제하지 않으면 {@link CycleIssuePort} 빈이 둘이 되어 애플리케이션이 뜨지 않는다.</p>
 */
@Service
public class EmptyCycleIssuePort implements CycleIssuePort {

    @Override
    public IssueStats statsOf(Long cycleId) {
        return IssueStats.EMPTY;
    }

    @Override
    public boolean hasAnyIssue(Long cycleId) {
        return false;
    }

    @Override
    public int moveUnfinishedIssues(Long fromCycleId, Long toCycleId) {
        return 0;
    }
}
