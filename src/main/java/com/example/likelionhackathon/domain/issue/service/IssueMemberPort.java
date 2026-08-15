package com.example.likelionhackathon.domain.issue.service;

import java.util.Optional;

/**
 * 이슈가 프로젝트 멤버 도메인에 물어봐야 하는 것들.
 *
 * <p>두 도메인이 서로를 직접 참조하지 않도록 이 인터페이스로 끊고,
 * {@link JpaIssueMemberPort} 가 프로젝트 멤버 테이블에서 조회한다.</p>
 */
public interface IssueMemberPort {

    Optional<MemberProfile> findProfile(Long memberId);

    /**
     * 지금 요청을 보낸 사용자를 해당 사이클이 속한 프로젝트의 멤버로 찾는다.
     * 활동 기록의 행위자 이름에 쓴다.
     */
    Optional<MemberProfile> findCurrentMember(Long cycleId);

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
