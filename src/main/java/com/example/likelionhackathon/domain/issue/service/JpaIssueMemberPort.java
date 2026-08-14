package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectMember;
import com.example.likelionhackathon.domain.project.entity.ProjectTeam;
import com.example.likelionhackathon.domain.project.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 담당자 정보를 프로젝트 멤버에서 가져온다.
 *
 * <p>이슈의 assigneeId 는 {@link ProjectMember} 의 식별자다.
 * 직책(position)은 ProjectMember 에도 User 에도 없어 프로젝트 내 역할(role)로 대신한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaIssueMemberPort implements IssueMemberPort {

    private final ProjectMemberRepository projectMemberRepository;
    private final CycleRepository cycleRepository;

    @Override
    public Optional<MemberProfile> findProfile(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }

        return projectMemberRepository.findById(memberId)
                .map(member -> new MemberProfile(
                        member.getId(),
                        member.getName(),
                        member.getCompanyName(),
                        teamNameOf(member),
                        member.getRole() == null ? null : member.getRole().name()
                ));
    }

    @Override
    public boolean isProjectMember(Long cycleId, Long memberId) {
        if (memberId == null) {
            return false;
        }

        Optional<Cycle> cycle = cycleRepository.findById(cycleId);
        if (cycle.isEmpty()) {
            return false;
        }

        return projectMemberRepository
                .findByIdAndProjectId(memberId, cycle.get().getProjectId())
                .filter(member -> member.getStatus() == ProjectMemberStatus.ACTIVE)
                .isPresent();
    }

    private String teamNameOf(ProjectMember member) {
        ProjectTeam team = member.getTeam();
        return team == null ? null : team.getTeamName();
    }
}
