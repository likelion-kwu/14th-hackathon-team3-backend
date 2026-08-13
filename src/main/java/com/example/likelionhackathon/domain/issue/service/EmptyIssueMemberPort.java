package com.example.likelionhackathon.domain.issue.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 회원 도메인이 없는 동안 쓰는 임시 구현.
 *
 * <p>이름 · 소속 · 직책은 채울 방법이 없어 null 로 둔다. 지어내면 화면에 가짜 데이터가 보이므로
 * 비워 두는 편이 낫다. 멤버 검증도 통과시킨다.</p>
 *
 * <p>⚠️ 회원/프로젝트 도메인이 생기면 이 클래스를 삭제하고 실제 구현으로 교체한다.
 * 삭제하지 않으면 빈이 둘이 되어 애플리케이션이 뜨지 않는다.</p>
 */
@Service
public class EmptyIssueMemberPort implements IssueMemberPort {

    @Override
    public Optional<MemberProfile> findProfile(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return Optional.of(new MemberProfile(memberId, null, null, null, null));
    }

    @Override
    public boolean isProjectMember(Long cycleId, Long memberId) {
        return true;
    }
}
