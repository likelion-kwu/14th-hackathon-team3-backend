package com.example.likelionhackathon.domain.issue.service;

import java.util.Optional;

/**
 * 이슈가 회원 도메인에 물어봐야 하는 것들.
 *
 * <p>담당자 이름 · 소속 · 직책과 프로젝트 멤버 여부는 회원/프로젝트 도메인 소관인데
 * 아직 구현되지 않았다. 그때까지 {@link EmptyIssueMemberPort} 가 식별자만 돌려준다.</p>
 */
public interface IssueMemberPort {

    Optional<MemberProfile> findProfile(Long memberId);

    boolean isProjectMember(Long cycleId, Long memberId);

    record MemberProfile(
            Long userId,
            String name,
            String company,
            String team,
            String position
    ) {
    }
}
