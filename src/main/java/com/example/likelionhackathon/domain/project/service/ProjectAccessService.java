package com.example.likelionhackathon.domain.project.service;

import com.example.likelionhackathon.domain.project.entity.Project;
import com.example.likelionhackathon.domain.project.entity.ProjectEnums.ProjectMemberStatus;
import com.example.likelionhackathon.domain.project.entity.ProjectMember;
import com.example.likelionhackathon.domain.project.repository.ProjectMemberRepository;
import com.example.likelionhackathon.domain.project.repository.ProjectRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final CurrentUserProvider currentUserProvider;

    public Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
    }

    public ProjectMember requireAccess(Long projectId) {
        return memberRepository.findByProjectIdAndPrincipalKey(
                        projectId,
                        currentUserProvider.currentPrincipalKey()
                )
                .filter(member -> member.getStatus() == ProjectMemberStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_ACCESS_DENIED));
    }

    public ProjectMember requireAdmin(Long projectId) {
        ProjectMember member = requireAccess(projectId);
        if (!member.getRole().canManage()) {
            throw new CustomException(ErrorCode.PROJECT_ADMIN_REQUIRED);
        }
        return member;
    }
}
