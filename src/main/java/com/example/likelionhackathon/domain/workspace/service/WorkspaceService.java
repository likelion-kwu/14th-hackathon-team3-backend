package com.example.likelionhackathon.domain.workspace.service;

import com.example.likelionhackathon.domain.workspace.dto.WorkspaceRequest;
import com.example.likelionhackathon.domain.workspace.dto.WorkspaceResponse;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.InvitationStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.InvitationType;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.MemberActionType;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberViewStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceInvitation;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceInvitationRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final int DEFAULT_INVITATION_EXPIRY_HOURS = 72;
    private static final int MAX_INVITATION_EXPIRY_HOURS = 720;
    private static final String ORGANIZATION_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceInvitationRepository invitationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final WorkspaceProjectCounter projectCounter;
    private final UserRepository userRepository;

    @Value("${workspace.invitation-base-url:https://relai.example.com/invite}")
    private String invitationBaseUrl;

    @Transactional
    public WorkspaceResponse.Created create(WorkspaceRequest.Create request) {
        validateCreateRequest(request);
        String workspaceName = request.name().trim();
        if (workspaceRepository.existsByNameIgnoreCase(workspaceName)) {
            throw new CustomException(ErrorCode.WORKSPACE_NAME_DUPLICATED);
        }

        String countryCode = normalizeCountryCode(request.companyCountryCode());
        Workspace workspace = Workspace.create(
                workspaceName,
                generateOrganizationCode(countryCode),
                request.companyName().trim(),
                countryCode,
                normalizeNames(request.safeCollaboratingCompanyNames())
        );

        try {
            Workspace saved = workspaceRepository.saveAndFlush(workspace);
            String principalKey = currentUserProvider.currentPrincipalKey();
            memberRepository.save(WorkspaceMember.createOwner(
                    saved,
                    principalKey,
                    principalKey,
                    saved.getCompanyName()
            ));
            createInitialInvitations(saved, request.safeInviteeEmails());
            return new WorkspaceResponse.Created(
                    saved.getId(),
                    saved.getOrganizationCode(),
                    saved.getStatus()
            );
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.WORKSPACE_NAME_DUPLICATED);
        }
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse.Summary> getWorkspaces(WorkspaceStatus status) {
        String principalKey = currentUserProvider.currentPrincipalKey();
        return memberRepository
                .findAllByPrincipalKeyAndStatusOrderByIdAsc(principalKey, WorkspaceMemberStatus.ACTIVE)
                .stream()
                .filter(member -> status == null || member.getWorkspace().getStatus() == status)
                .map(member -> new WorkspaceResponse.Summary(
                        member.getWorkspace().getId(),
                        member.getWorkspace().getName(),
                        member.getWorkspace().getCompanyName(),
                        member.getRole(),
                        memberRepository.countByWorkspaceIdAndStatus(
                                member.getWorkspace().getId(),
                                WorkspaceMemberStatus.ACTIVE
                        ),
                        member.getWorkspace().getStatus()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse.Profile getMyProfile(Long workspaceId) {
        String principalKey = currentUserProvider.currentPrincipalKey();
        WorkspaceMember member = requireAccess(workspaceId, principalKey);
        return toProfile(workspaceId, principalKey, member);
    }

    @Transactional
    public WorkspaceResponse.Profile updateMyProfile(
            Long workspaceId,
            WorkspaceRequest.UpdateProfile request
    ) {
        validateProfileUpdate(request);
        String principalKey = currentUserProvider.currentPrincipalKey();
        WorkspaceMember member = requireAccess(workspaceId, principalKey);

        member.updateProfile(
                normalizeRequiredProfileValue(request.name()),
                normalizeRequiredProfileValue(request.companyName()),
                normalizeRequiredProfileValue(request.teamName()),
                request.jobTitle() == null
                        ? member.getJobTitle()
                        : normalizeOptionalProfileValue(request.jobTitle())
        );
        return toProfile(workspaceId, principalKey, member);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse.Detail getDetail(Long workspaceId) {
        Workspace workspace = findWorkspace(workspaceId);
        WorkspaceMember membership = requireAccess(workspaceId);
        return new WorkspaceResponse.Detail(
                workspace.getId(),
                workspace.getName(),
                workspace.getOrganizationCode(),
                new WorkspaceResponse.Company(
                        workspace.getCompanyName(),
                        workspace.getCompanyCountryCode()
                ),
                List.copyOf(workspace.getCollaboratingCompanyNames()),
                membership.getRole(),
                memberRepository.countByWorkspaceIdAndStatus(
                        workspaceId,
                        WorkspaceMemberStatus.ACTIVE
                ),
                projectCounter.countByWorkspaceId(workspaceId),
                workspace.getStatus()
        );
    }

    @Transactional
    public WorkspaceResponse.Updated update(Long workspaceId, WorkspaceRequest.Update request) {
        validateUpdateRequest(request);
        Workspace workspace = findWorkspace(workspaceId);
        requireAdmin(workspaceId);
        validateVersion(request.version(), workspace.getVersion());

        String workspaceName = request.name().trim();
        if (workspaceRepository.existsByNameIgnoreCaseAndIdNot(workspaceName, workspaceId)) {
            throw new CustomException(ErrorCode.WORKSPACE_NAME_DUPLICATED);
        }

        workspace.update(
                workspaceName,
                request.companyName().trim(),
                normalizeCountryCode(request.companyCountryCode()),
                normalizeNames(request.safeCollaboratingCompanyNames()),
                request.status()
        );

        try {
            Workspace saved = workspaceRepository.saveAndFlush(workspace);
            return new WorkspaceResponse.Updated(
                    saved.getId(),
                    saved.getStatus(),
                    normalizeVersion(saved.getVersion())
            );
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CustomException(ErrorCode.WORKSPACE_VERSION_CONFLICT);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.WORKSPACE_NAME_DUPLICATED);
        }
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse.Members getMembers(
            Long workspaceId,
            WorkspaceMemberViewStatus status,
            String keyword
    ) {
        findWorkspace(workspaceId);
        requireAccess(workspaceId);

        boolean includeMembers = status == null || status != WorkspaceMemberViewStatus.INVITED;
        boolean includeInvitations = status == null || status == WorkspaceMemberViewStatus.INVITED;
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<WorkspaceResponse.Member> members = includeMembers
                ? memberRepository.findAllByWorkspaceIdOrderByIdAsc(workspaceId).stream()
                .filter(member -> matchesMemberStatus(member, status))
                .filter(member -> matchesKeyword(member.getName(), member.getEmail(), normalizedKeyword))
                .map(this::toMemberResponse)
                .toList()
                : List.of();

        List<WorkspaceResponse.PendingInvitation> pendingInvitations = includeInvitations
                ? invitationRepository
                .findAllByWorkspaceIdAndStatusOrderByIdAsc(workspaceId, InvitationStatus.PENDING)
                .stream()
                .filter(invitation -> matchesKeyword(invitation.getEmail(), null, normalizedKeyword))
                .map(invitation -> new WorkspaceResponse.PendingInvitation(
                        invitation.getId(),
                        invitation.getEmail(),
                        invitation.getRole()
                ))
                .toList()
                : List.of();

        return new WorkspaceResponse.Members(members, pendingInvitations);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse.OrganizationChart getOrganizationChart(Long workspaceId) {
        findWorkspace(workspaceId);
        requireAccess(workspaceId);

        List<WorkspaceMember> members = memberRepository
                .findAllByWorkspaceIdAndStatusOrderByIdAsc(workspaceId, WorkspaceMemberStatus.ACTIVE);
        Map<Long, User> usersById = findUsersById(members);

        Map<String, List<WorkspaceResponse.OrganizationMember>> membersByTeam = new LinkedHashMap<>();
        for (WorkspaceMember member : members) {
            Long userId = parseUserId(member.getPrincipalKey());
            User user = usersById.get(userId);
            if (user == null) {
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }
            membersByTeam.computeIfAbsent(member.getTeamName(), ignored -> new ArrayList<>())
                    .add(toOrganizationMember(member, user));
        }

        Comparator<WorkspaceResponse.OrganizationMember> memberComparator = Comparator
                .comparing(WorkspaceResponse.OrganizationMember::name)
                .thenComparing(WorkspaceResponse.OrganizationMember::memberId);
        Comparator<String> teamComparator = Comparator.nullsLast(Comparator.naturalOrder());

        List<WorkspaceResponse.OrganizationTeam> teams = membersByTeam.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(teamComparator))
                .map(entry -> new WorkspaceResponse.OrganizationTeam(
                        entry.getKey(),
                        entry.getValue().stream().sorted(memberComparator).toList()
                ))
                .toList();

        return new WorkspaceResponse.OrganizationChart(workspaceId, teams);
    }

    private Map<Long, User> findUsersById(List<WorkspaceMember> members) {
        List<Long> userIds = members.stream()
                .map(WorkspaceMember::getPrincipalKey)
                .map(this::parseUserId)
                .distinct()
                .toList();
        Map<Long, User> usersById = new LinkedHashMap<>();
        userRepository.findAllById(userIds).forEach(user -> usersById.put(user.getId(), user));
        if (usersById.size() != userIds.size()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return usersById;
    }

    private Long parseUserId(String principalKey) {
        try {
            return Long.valueOf(principalKey);
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private WorkspaceResponse.OrganizationMember toOrganizationMember(
            WorkspaceMember member,
            User user
    ) {
        return new WorkspaceResponse.OrganizationMember(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getCompanyName(),
                member.getJobTitle(),
                user.getActivityStatus()
        );
    }

    @Transactional
    public WorkspaceResponse.MembersManaged manageMembers(
            Long workspaceId,
            WorkspaceRequest.ManageMembers request
    ) {
        findWorkspace(workspaceId);
        requireAdmin(workspaceId);
        if (request == null || request.actions() == null || request.actions().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_WORKSPACE_INPUT);
        }

        for (WorkspaceRequest.MemberAction action : request.actions()) {
            validateMemberAction(action);
            WorkspaceMember target = memberRepository
                    .findByIdAndWorkspaceId(action.memberId(), workspaceId)
                    .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
            protectLastOwner(workspaceId, target, action);
            applyMemberAction(target, action);
        }

        return new WorkspaceResponse.MembersManaged(request.actions().size(), List.of());
    }

    @Transactional
    public WorkspaceResponse.InvitationCreated createInvitation(
            Long workspaceId,
            WorkspaceRequest.CreateInvitation request
    ) {
        validateInvitationRequest(request);
        Workspace workspace = findWorkspace(workspaceId);
        requireAdmin(workspaceId);

        int expiresInHours = request.expiresInHours() == null
                ? DEFAULT_INVITATION_EXPIRY_HOURS
                : request.expiresInHours();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(expiresInHours);
        List<String> emails = normalizeEmails(request.safeEmails());

        if (request.type() == InvitationType.EMAIL) {
            for (String email : emails) {
                if (memberRepository.existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(
                        workspaceId,
                        email,
                        WorkspaceMemberStatus.ACTIVE
                )) {
                    throw new CustomException(ErrorCode.ALREADY_WORKSPACE_MEMBER);
                }
            }
        }

        List<WorkspaceInvitation> invitations = new ArrayList<>();
        if (request.type() == InvitationType.LINK) {
            invitations.add(newInvitation(workspace, request.type(), null, request.role(), expiresAt));
        } else {
            emails.forEach(email -> invitations.add(
                    newInvitation(workspace, request.type(), email, request.role(), expiresAt)
            ));
        }

        List<WorkspaceInvitation> saved = invitationRepository.saveAll(invitations);
        WorkspaceInvitation primary = saved.getFirst();
        return new WorkspaceResponse.InvitationCreated(
                primary.getId(),
                invitationBaseUrl + "?token=" + primary.getToken(),
                request.type() == InvitationType.EMAIL ? saved.size() : 0,
                primary.getExpiresAt()
        );
    }

    @Transactional
    public WorkspaceResponse.Joined join(WorkspaceRequest.JoinInvitation request) {
        WorkspaceInvitation invitation = invitationRepository.findByToken(request.inviteToken())
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));
        String principalKey = currentUserProvider.currentPrincipalKey();
        Long workspaceId = invitation.getWorkspace().getId();

        if (memberRepository.existsByWorkspaceIdAndPrincipalKey(workspaceId, principalKey)) {
            throw new CustomException(ErrorCode.ALREADY_WORKSPACE_MEMBER);
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new CustomException(ErrorCode.INVITATION_NOT_FOUND);
        }
        if (invitation.isExpired(OffsetDateTime.now())) {
            throw new CustomException(ErrorCode.INVITATION_EXPIRED);
        }

        WorkspaceMember member = WorkspaceMember.createInvitedMember(
                invitation.getWorkspace(),
                principalKey,
                request.name().trim(),
                invitation.getEmail(),
                request.companyName().trim(),
                request.teamName().trim(),
                trimToNull(request.jobTitle()),
                invitation.getRole()
        );
        WorkspaceMember saved = memberRepository.save(member);
        invitation.accept();

        return new WorkspaceResponse.Joined(workspaceId, saved.getId(), saved.getRole());
    }

    private void createInitialInvitations(Workspace workspace, Collection<String> inviteeEmails) {
        List<String> emails = normalizeEmails(inviteeEmails);
        if (emails.isEmpty()) {
            return;
        }
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(DEFAULT_INVITATION_EXPIRY_HOURS);
        List<WorkspaceInvitation> invitations = emails.stream()
                .map(email -> WorkspaceInvitation.create(
                        workspace,
                        InvitationType.EMAIL,
                        email,
                        WorkspaceRole.MEMBER,
                        generateInvitationToken(),
                        expiresAt
                ))
                .toList();
        invitationRepository.saveAll(invitations);
    }

    private WorkspaceInvitation newInvitation(
            Workspace workspace,
            InvitationType type,
            String email,
            com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.AssignableWorkspaceRole role,
            OffsetDateTime expiresAt
    ) {
        return WorkspaceInvitation.create(
                workspace,
                type,
                email,
                role.toWorkspaceRole(),
                generateInvitationToken(),
                expiresAt
        );
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private WorkspaceMember requireAccess(Long workspaceId) {
        return requireAccess(workspaceId, currentUserProvider.currentPrincipalKey());
    }

    private WorkspaceMember requireAccess(Long workspaceId, String principalKey) {
        return memberRepository.findByWorkspaceIdAndPrincipalKey(workspaceId, principalKey)
                .filter(member -> member.getStatus() == WorkspaceMemberStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSPACE_ACCESS_DENIED));
    }

    private WorkspaceResponse.Profile toProfile(
            Long workspaceId,
            String principalKey,
            WorkspaceMember member
    ) {
        return new WorkspaceResponse.Profile(
                Long.valueOf(principalKey),
                workspaceId,
                member.getName(),
                member.getCompanyName(),
                member.getTeamName(),
                member.getJobTitle()
        );
    }

    private void validateProfileUpdate(WorkspaceRequest.UpdateProfile request) {
        if (request.name() == null
                && request.companyName() == null
                && request.teamName() == null
                && request.jobTitle() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateRequiredProfileValue(request.name());
        validateRequiredProfileValue(request.companyName());
        validateRequiredProfileValue(request.teamName());
    }

    private void validateRequiredProfileValue(String value) {
        if (value != null && value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String normalizeRequiredProfileValue(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalProfileValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private WorkspaceMember requireAdmin(Long workspaceId) {
        WorkspaceMember member = requireAccess(workspaceId);
        if (!member.getRole().canManage()) {
            throw new CustomException(ErrorCode.WORKSPACE_ADMIN_REQUIRED);
        }
        return member;
    }

    private void protectLastOwner(
            Long workspaceId,
            WorkspaceMember target,
            WorkspaceRequest.MemberAction action
    ) {
        boolean removesOwnerRole = action.action() == MemberActionType.SUSPEND
                || action.action() == MemberActionType.REMOVE
                || (action.action() == MemberActionType.UPDATE && action.role() != null);
        if (target.getRole() == WorkspaceRole.OWNER
                && removesOwnerRole
                && memberRepository.countByWorkspaceIdAndRoleAndStatus(
                workspaceId,
                WorkspaceRole.OWNER,
                WorkspaceMemberStatus.ACTIVE
        ) <= 1) {
            throw new CustomException(ErrorCode.LAST_OWNER_CANNOT_CHANGE);
        }
    }

    private void applyMemberAction(WorkspaceMember target, WorkspaceRequest.MemberAction action) {
        switch (action.action()) {
            case UPDATE -> target.update(
                    action.role(),
                    trimToNull(action.teamName()),
                    trimToNull(action.jobTitle())
            );
            case SUSPEND -> target.suspend();
            case REMOVE -> memberRepository.delete(target);
        }
    }

    private void validateCreateRequest(WorkspaceRequest.Create request) {
        if (request == null
                || isBlankOrTooLong(request.name(), 100)
                || isBlankOrTooLong(request.companyName(), 100)
                || request.companyCountryCode() == null
                || request.companyCountryCode().trim().length() != 2
                || hasInvalidName(request.safeCollaboratingCompanyNames())
                || hasInvalidEmail(request.safeInviteeEmails())) {
            throw new CustomException(ErrorCode.INVALID_WORKSPACE_INPUT);
        }
    }

    private void validateUpdateRequest(WorkspaceRequest.Update request) {
        if (request == null
                || isBlankOrTooLong(request.name(), 100)
                || isBlankOrTooLong(request.companyName(), 100)
                || request.companyCountryCode() == null
                || request.companyCountryCode().trim().length() != 2
                || request.status() == null
                || request.version() == null
                || hasInvalidName(request.safeCollaboratingCompanyNames())) {
            throw new CustomException(ErrorCode.INVALID_WORKSPACE_INPUT);
        }
    }

    private void validateInvitationRequest(WorkspaceRequest.CreateInvitation request) {
        if (request == null
                || request.type() == null
                || request.role() == null
                || (request.expiresInHours() != null
                && (request.expiresInHours() < 1 || request.expiresInHours() > MAX_INVITATION_EXPIRY_HOURS))) {
            throw new CustomException(ErrorCode.INVALID_INVITATION_INPUT);
        }
        List<String> emails = request.safeEmails();
        if ((request.type() == InvitationType.EMAIL && (emails.isEmpty() || hasInvalidEmail(emails)))
                || (request.type() == InvitationType.LINK && !emails.isEmpty())) {
            throw new CustomException(ErrorCode.INVALID_INVITATION_INPUT);
        }
    }

    private void validateMemberAction(WorkspaceRequest.MemberAction action) {
        if (action == null || action.action() == null || action.memberId() == null) {
            throw new CustomException(ErrorCode.INVALID_WORKSPACE_INPUT);
        }
    }

    private void validateVersion(Long requestedVersion, Long currentVersion) {
        if (!Objects.equals(requestedVersion, normalizeVersion(currentVersion))) {
            throw new CustomException(ErrorCode.WORKSPACE_VERSION_CONFLICT);
        }
    }

    private Long normalizeVersion(Long version) {
        return version == null ? 0L : version;
    }

    private boolean matchesMemberStatus(WorkspaceMember member, WorkspaceMemberViewStatus status) {
        if (status == null) {
            return true;
        }
        return switch (status) {
            case ACTIVE -> member.getStatus() == WorkspaceMemberStatus.ACTIVE;
            case SUSPENDED -> member.getStatus() == WorkspaceMemberStatus.SUSPENDED;
            case INVITED -> false;
        };
    }

    private boolean matchesKeyword(String first, String second, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(first, keyword) || containsIgnoreCase(second, keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private WorkspaceResponse.Member toMemberResponse(WorkspaceMember member) {
        return new WorkspaceResponse.Member(
                member.getId(),
                member.getName(),
                member.getCompanyName(),
                member.getTeamName(),
                member.getRole(),
                member.getStatus()
        );
    }

    private String generateOrganizationCode(String countryCode) {
        String organizationCode;
        do {
            StringBuilder suffix = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                suffix.append(ORGANIZATION_CODE_CHARACTERS.charAt(
                        SECURE_RANDOM.nextInt(ORGANIZATION_CODE_CHARACTERS.length())
                ));
            }
            organizationCode = "RELAI-" + countryCode + "-" + suffix;
        } while (workspaceRepository.existsByOrganizationCode(organizationCode));
        return organizationCode;
    }

    private String generateInvitationToken() {
        return "ws_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizeCountryCode(String countryCode) {
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeNames(Collection<String> names) {
        return names.stream()
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> normalizeEmails(Collection<String> emails) {
        Set<String> normalized = new LinkedHashSet<>();
        emails.forEach(email -> normalized.add(email.trim().toLowerCase(Locale.ROOT)));
        return List.copyOf(normalized);
    }

    private boolean hasInvalidName(Collection<String> names) {
        return names.stream().anyMatch(name -> isBlankOrTooLong(name, 100));
    }

    private boolean hasInvalidEmail(Collection<String> emails) {
        return emails.stream().anyMatch(email -> email == null
                || email.length() > 255
                || !EMAIL_PATTERN.matcher(email.trim()).matches());
    }

    private boolean isBlankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.trim().length() > maxLength;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
