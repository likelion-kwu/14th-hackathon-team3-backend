package com.example.likelionhackathon.domain.workspace.service;

import com.example.likelionhackathon.domain.workspace.dto.WorkspaceRequest;
import com.example.likelionhackathon.domain.workspace.dto.WorkspaceResponse;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.entity.UserEnums.ActivityStatus;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.AssignableWorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.InvitationStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.InvitationType;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.MemberActionType;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceRole;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceInvitation;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceInvitationRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Mock
    private WorkspaceInvitationRepository invitationRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private WorkspaceProjectCounter projectCounter;

    @Mock
    private UserRepository userRepository;

    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(
                workspaceRepository,
                memberRepository,
                invitationRepository,
                currentUserProvider,
                projectCounter,
                userRepository
        );
        ReflectionTestUtils.setField(
                workspaceService,
                "invitationBaseUrl",
                "https://relai.example.com/invite"
        );
    }

    @Test
    void createWorkspaceCreatesOwnerAndPendingInvitations() {
        WorkspaceRequest.Create request = new WorkspaceRequest.Create(
                "Global Payment",
                "RelAI",
                "kr",
                List.of("Partner A"),
                List.of("partner@example.com")
        );
        when(currentUserProvider.currentPrincipalKey()).thenReturn("1");
        when(workspaceRepository.saveAndFlush(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace workspace = invocation.getArgument(0);
            ReflectionTestUtils.setField(workspace, "id", 10L);
            return workspace;
        });
        when(invitationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<WorkspaceInvitation> invitations = invocation.getArgument(0);
            AtomicLong sequence = new AtomicLong(100L);
            invitations.forEach(invitation -> ReflectionTestUtils.setField(
                    invitation,
                    "id",
                    sequence.getAndIncrement()
            ));
            return invitations;
        });

        WorkspaceResponse.Created response = workspaceService.create(request);

        assertThat(response.workspaceId()).isEqualTo(10L);
        assertThat(response.organizationCode()).startsWith("RELAI-KR-");
        assertThat(response.status()).isEqualTo(WorkspaceStatus.ACTIVE);
        verify(memberRepository).save(any(WorkspaceMember.class));
        verify(invitationRepository).saveAll(any());
    }

    @Test
    void createWorkspaceRejectsDuplicateName() {
        WorkspaceRequest.Create request = new WorkspaceRequest.Create(
                "Global Payment",
                "RelAI",
                "KR",
                List.of(),
                List.of()
        );
        when(workspaceRepository.existsByNameIgnoreCase("Global Payment")).thenReturn(true);

        assertThatThrownBy(() -> workspaceService.create(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORKSPACE_NAME_DUPLICATED);
    }

    @Test
    void updateWorkspaceRejectsStaleVersion() {
        Workspace workspace = workspace(10L, 3L);
        WorkspaceMember owner = owner(workspace, 20L);
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("1");
        when(memberRepository.findByWorkspaceIdAndPrincipalKey(10L, "1"))
                .thenReturn(Optional.of(owner));

        WorkspaceRequest.Update request = new WorkspaceRequest.Update(
                "Renamed",
                "RelAI",
                "KR",
                List.of(),
                WorkspaceStatus.ACTIVE,
                2L
        );

        assertThatThrownBy(() -> workspaceService.update(10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORKSPACE_VERSION_CONFLICT);
    }

    @Test
    void detailRequiresActiveMembership() {
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace(10L, 0L)));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("outsider");
        when(memberRepository.findByWorkspaceIdAndPrincipalKey(10L, "outsider"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getDetail(10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORKSPACE_ACCESS_DENIED);
    }

    @Test
    void manageMembersCannotRemoveLastOwner() {
        Workspace workspace = workspace(10L, 0L);
        WorkspaceMember owner = owner(workspace, 20L);
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("1");
        when(memberRepository.findByWorkspaceIdAndPrincipalKey(10L, "1"))
                .thenReturn(Optional.of(owner));
        when(memberRepository.findByIdAndWorkspaceId(20L, 10L)).thenReturn(Optional.of(owner));
        when(memberRepository.countByWorkspaceIdAndRoleAndStatus(any(), any(), any())).thenReturn(1L);

        WorkspaceRequest.ManageMembers request = new WorkspaceRequest.ManageMembers(List.of(
                new WorkspaceRequest.MemberAction(
                        MemberActionType.REMOVE,
                        20L,
                        null,
                        null,
                        null
                )
        ));

        assertThatThrownBy(() -> workspaceService.manageMembers(10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LAST_OWNER_CANNOT_CHANGE);
    }

    @Test
    void createInvitationRejectsInvalidEmailRequest() {
        WorkspaceRequest.CreateInvitation request = new WorkspaceRequest.CreateInvitation(
                InvitationType.EMAIL,
                List.of("invalid-email"),
                AssignableWorkspaceRole.MEMBER,
                24
        );

        assertThatThrownBy(() -> workspaceService.createInvitation(10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INVITATION_INPUT);
    }

    @Test
    void joinRejectsExpiredInvitation() {
        Workspace workspace = workspace(10L, 0L);
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                workspace,
                InvitationType.LINK,
                null,
                WorkspaceRole.MEMBER,
                "ws_expired",
                OffsetDateTime.now().minusMinutes(1)
        );
        when(invitationRepository.findByToken("ws_expired")).thenReturn(Optional.of(invitation));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("2");

        WorkspaceRequest.JoinInvitation request = new WorkspaceRequest.JoinInvitation(
                "ws_expired",
                "Emily",
                "Partner",
                "Engineering",
                "Backend"
        );

        assertThatThrownBy(() -> workspaceService.join(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVITATION_EXPIRED);
    }

    @Test
    void joinCreatesActiveWorkspaceMember() {
        Workspace workspace = workspace(10L, 0L);
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                workspace,
                InvitationType.EMAIL,
                "emily@example.com",
                WorkspaceRole.ADMIN,
                "ws_valid",
                OffsetDateTime.now().plusHours(1)
        );
        when(invitationRepository.findByToken("ws_valid")).thenReturn(Optional.of(invitation));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("2");
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(invocation -> {
            WorkspaceMember member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 30L);
            return member;
        });

        WorkspaceResponse.Joined response = workspaceService.join(
                new WorkspaceRequest.JoinInvitation(
                        "ws_valid",
                        "Emily",
                        "Partner",
                        "Engineering",
                        "Backend"
                )
        );

        assertThat(response.workspaceId()).isEqualTo(10L);
        assertThat(response.memberId()).isEqualTo(30L);
        assertThat(response.role()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    void organizationChartGroupsAndSortsActiveMembersWithUserActivityStatus() {
        Workspace workspace = workspace(10L, 0L);
        WorkspaceMember requester = member(workspace, 30L, "1", "Zoe", "RelAI", "Engineering", "Lead");
        WorkspaceMember engineering = member(workspace, 20L, "2", "Amy", "Partner", "Engineering", null);
        WorkspaceMember design = member(workspace, 40L, "3", "Bob", "RelAI", "Design", "Designer");
        WorkspaceMember unassigned = member(workspace, 50L, "4", "Kim", "RelAI", null, null);
        WorkspaceMember suspended = member(workspace, 60L, "5", "Hidden", "RelAI", "Product", null);
        suspended.suspend();
        User user1 = user(1L, ActivityStatus.ACTIVE);
        User user2 = user(2L, ActivityStatus.OFF);
        User user3 = user(3L, ActivityStatus.ACTIVE);
        User user4 = user(4L, ActivityStatus.OFF);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("1");
        when(memberRepository.findByWorkspaceIdAndPrincipalKey(10L, "1"))
                .thenReturn(Optional.of(requester));
        when(memberRepository.findAllByWorkspaceIdAndStatusOrderByIdAsc(
                10L, WorkspaceMemberStatus.ACTIVE
        )).thenReturn(List.of(requester, engineering, design, unassigned));
        when(userRepository.findAllById(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(user1, user2, user3, user4));

        WorkspaceResponse.OrganizationChart response = workspaceService.getOrganizationChart(10L);

        assertThat(response.workspaceId()).isEqualTo(10L);
        assertThat(response.teams()).extracting(WorkspaceResponse.OrganizationTeam::teamName)
                .containsExactly("Design", "Engineering", null);
        assertThat(response.teams().get(1).members())
                .extracting(WorkspaceResponse.OrganizationMember::name)
                .containsExactly("Amy", "Zoe");
        assertThat(response.teams().get(1).members().getFirst().activityStatus())
                .isEqualTo(ActivityStatus.OFF);
        assertThat(response.teams().get(1).members().getFirst().jobTitle()).isNull();
        assertThat(response.teams().getLast().members().getFirst().activityStatus())
                .isEqualTo(ActivityStatus.OFF);
        verify(userRepository).findAllById(List.of(1L, 2L, 3L, 4L));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void organizationChartRejectsMissingUser() {
        Workspace workspace = workspace(10L, 0L);
        WorkspaceMember requester = member(workspace, 20L, "1", "Owner", "RelAI", null, null);
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("1");
        when(memberRepository.findByWorkspaceIdAndPrincipalKey(10L, "1"))
                .thenReturn(Optional.of(requester));
        when(memberRepository.findAllByWorkspaceIdAndStatusOrderByIdAsc(
                10L, WorkspaceMemberStatus.ACTIVE
        )).thenReturn(List.of(requester));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> workspaceService.getOrganizationChart(10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void organizationChartRejectsNonNumericPrincipalKey() {
        Workspace workspace = workspace(10L, 0L);
        WorkspaceMember requester = member(
                workspace, 20L, "invalid", "Owner", "RelAI", null, null
        );
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(currentUserProvider.currentPrincipalKey()).thenReturn("invalid");
        when(memberRepository.findByWorkspaceIdAndPrincipalKey(10L, "invalid"))
                .thenReturn(Optional.of(requester));
        when(memberRepository.findAllByWorkspaceIdAndStatusOrderByIdAsc(
                10L, WorkspaceMemberStatus.ACTIVE
        )).thenReturn(List.of(requester));

        assertThatThrownBy(() -> workspaceService.getOrganizationChart(10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        verify(userRepository, never()).findAllById(any());
    }

    private Workspace workspace(Long id, Long version) {
        Workspace workspace = Workspace.create(
                "Global Payment",
                "RELAI-KR-A7F2",
                "RelAI",
                "KR",
                List.of("Partner A")
        );
        ReflectionTestUtils.setField(workspace, "id", id);
        ReflectionTestUtils.setField(workspace, "version", version);
        return workspace;
    }

    private WorkspaceMember owner(Workspace workspace, Long id) {
        WorkspaceMember member = WorkspaceMember.createOwner(
                workspace,
                "1",
                "Owner",
                "RelAI"
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private WorkspaceMember member(
            Workspace workspace,
            Long id,
            String principalKey,
            String name,
            String companyName,
            String teamName,
            String jobTitle
    ) {
        WorkspaceMember member = WorkspaceMember.createInvitedMember(
                workspace,
                principalKey,
                name,
                null,
                companyName,
                teamName,
                jobTitle,
                WorkspaceRole.MEMBER
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private User user(Long id, ActivityStatus activityStatus) {
        User user = User.create("user" + id + "@example.com", "encoded", "User " + id);
        ReflectionTestUtils.setField(user, "id", id);
        user.changeActivityStatus(activityStatus);
        return user;
    }
}
