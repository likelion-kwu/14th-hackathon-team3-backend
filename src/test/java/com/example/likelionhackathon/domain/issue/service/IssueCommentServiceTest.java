package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.ActivityType;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.cycle.service.CycleActivityService;
import com.example.likelionhackathon.domain.issue.dto.IssueCommentRequest;
import com.example.likelionhackathon.domain.issue.dto.IssueCommentResponse;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueComment;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.repository.IssueCommentRepository;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import com.example.likelionhackathon.domain.issue.service.IssueMemberPort.MemberProfile;
import com.example.likelionhackathon.domain.project.service.ProjectAccessService;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueCommentServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long CYCLE_ID = 3L;
    private static final Long ISSUE_ID = 12L;
    private static final Long COMMENT_ID = 55L;
    private static final Long AUTHOR_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 9L;

    @Mock
    private IssueCommentRepository issueCommentRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private CycleRepository cycleRepository;

    @Mock
    private IssueMemberPort issueMemberPort;

    @Mock
    private CycleActivityService cycleActivityService;

    @Mock
    private ProjectAccessService projectAccessService;

    private IssueCommentService issueCommentService;

    @BeforeEach
    void setUp() {
        issueCommentService = new IssueCommentService(
                issueCommentRepository, issueRepository, cycleRepository,
                issueMemberPort, cycleActivityService, projectAccessService);
    }

    @Test
    void writeRejectsNonProjectMember() {
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        doThrow(new CustomException(ErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireAccess(PROJECT_ID);

        assertThatThrownBy(() -> issueCommentService.write(ISSUE_ID, new IssueCommentRequest.Write("안녕")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);

        verify(issueCommentRepository, never()).save(any());
    }

    @Test
    void deleteRejectsNonProjectMember() {
        when(issueCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment("안녕", AUTHOR_ID)));
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        doThrow(new CustomException(ErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireAccess(PROJECT_ID);

        assertThatThrownBy(() -> issueCommentService.delete(COMMENT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);

        verify(issueCommentRepository, never()).delete(any());
    }

    @Test
    void editRejectsAnotherMembersComment() {
        when(issueCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment("안녕", AUTHOR_ID)));
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.findCurrentMember(CYCLE_ID)).thenReturn(Optional.of(member(OTHER_MEMBER_ID, "남")));

        assertThatThrownBy(() -> issueCommentService.edit(COMMENT_ID, new IssueCommentRequest.Edit("고침")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_FORBIDDEN);
    }

    @Test
    void deleteRejectsAnotherMembersComment() {
        when(issueCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment("안녕", AUTHOR_ID)));
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.findCurrentMember(CYCLE_ID)).thenReturn(Optional.of(member(OTHER_MEMBER_ID, "남")));

        assertThatThrownBy(() -> issueCommentService.delete(COMMENT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_FORBIDDEN);

        verify(issueCommentRepository, never()).delete(any());
    }

    @Test
    void writeRecordsActivityQuotingTheComment() {
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.findCurrentMember(CYCLE_ID)).thenReturn(Optional.of(member(AUTHOR_ID, "김호균")));
        when(issueCommentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        issueCommentService.write(ISSUE_ID, new IssueCommentRequest.Write("내가 전달 받은 뒤 다시 공유해줄래?"));

        ArgumentCaptor<CycleActivity> captor = ArgumentCaptor.forClass(CycleActivity.class);
        verify(cycleActivityService).record(captor.capture());

        CycleActivity activity = captor.getValue();
        assertThat(activity.getType()).isEqualTo(ActivityType.COMMENT_ADDED);
        assertThat(activity.getActorName()).isEqualTo("김호균");
        assertThat(activity.getIssueId()).isEqualTo(ISSUE_ID);
        assertThat(activity.getIssueTitle()).isEqualTo("파트너사 데이터 연동 확인");
        assertThat(activity.getReason()).isEqualTo("내가 전달 받은 뒤 다시 공유해줄래?");
    }

    @Test
    void writeRejectsBlankContent() {
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));

        assertThatThrownBy(() -> issueCommentService.write(ISSUE_ID, new IssueCommentRequest.Write("   ")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_INVALID_INPUT);

        verify(issueCommentRepository, never()).save(any());
    }

    @Test
    void longCommentIsTruncatedInTheActivityLog() {
        String longContent = "가".repeat(IssueComment.EXCERPT_LENGTH + 50);

        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.findCurrentMember(CYCLE_ID)).thenReturn(Optional.of(member(AUTHOR_ID, "김호균")));
        when(issueCommentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        issueCommentService.write(ISSUE_ID, new IssueCommentRequest.Write(longContent));

        ArgumentCaptor<CycleActivity> captor = ArgumentCaptor.forClass(CycleActivity.class);
        verify(cycleActivityService).record(captor.capture());

        assertThat(captor.getValue().getReason())
                .hasSize(IssueComment.EXCERPT_LENGTH + 1)
                .endsWith("…");
    }

    @Test
    void freshCommentIsNotMarkedAsEdited() {
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.findCurrentMember(CYCLE_ID)).thenReturn(Optional.of(member(AUTHOR_ID, "김호균")));
        when(issueMemberPort.findProfile(AUTHOR_ID)).thenReturn(Optional.of(member(AUTHOR_ID, "김호균")));
        when(issueCommentRepository.findByIssueIdOrderByCreatedAtAscIdAsc(ISSUE_ID))
                .thenReturn(List.of(comment("갓 쓴 댓글", AUTHOR_ID)));

        assertThat(issueCommentService.getComments(ISSUE_ID))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.edited()).isFalse();
                    assertThat(item.updatedAt()).isNull();
                });
    }

    @Test
    void editMarksTheCommentAsEdited() {
        IssueComment comment = comment("처음", AUTHOR_ID);

        when(issueCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.findCurrentMember(CYCLE_ID)).thenReturn(Optional.of(member(AUTHOR_ID, "김호균")));

        IssueCommentResponse.Item edited =
                issueCommentService.edit(COMMENT_ID, new IssueCommentRequest.Edit("고침"));

        assertThat(edited.content()).isEqualTo("고침");
        assertThat(edited.edited()).isTrue();
        assertThat(edited.editable()).isTrue();
    }

    @Test
    void getCommentsMarksOnlyOwnCommentsEditable() {
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.findCurrentMember(CYCLE_ID)).thenReturn(Optional.of(member(AUTHOR_ID, "김호균")));
        when(issueMemberPort.findProfile(AUTHOR_ID)).thenReturn(Optional.of(member(AUTHOR_ID, "김호균")));
        when(issueMemberPort.findProfile(OTHER_MEMBER_ID)).thenReturn(Optional.of(member(OTHER_MEMBER_ID, "남")));
        when(issueCommentRepository.findByIssueIdOrderByCreatedAtAscIdAsc(ISSUE_ID))
                .thenReturn(List.of(comment("내 댓글", AUTHOR_ID), comment("남의 댓글", OTHER_MEMBER_ID)));

        List<IssueCommentResponse.Item> comments = issueCommentService.getComments(ISSUE_ID);

        assertThat(comments).extracting(IssueCommentResponse.Item::authorName)
                .containsExactly("김호균", "남");
        assertThat(comments).extracting(IssueCommentResponse.Item::editable)
                .containsExactly(true, false);
    }

    private Cycle cycle() {
        Cycle cycle = Cycle.create(
                PROJECT_ID, "7월 4주차", LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12), "목표");
        ReflectionTestUtils.setField(cycle, "id", CYCLE_ID);
        return cycle;
    }

    private Issue issue() {
        Issue issue = Issue.create(
                CYCLE_ID, "파트너사 데이터 연동 확인", "설명",
                IssuePriority.HIGH, AUTHOR_ID, LocalDate.of(2026, 8, 5));
        ReflectionTestUtils.setField(issue, "id", ISSUE_ID);
        return issue;
    }

    private IssueComment comment(String content, Long authorId) {
        IssueComment comment = IssueComment.write(ISSUE_ID, authorId, content);
        ReflectionTestUtils.setField(comment, "id", COMMENT_ID);
        return comment;
    }

    private MemberProfile member(Long memberId, String name) {
        return new MemberProfile(memberId, name, "회사", "팀", "MEMBER");
    }
}
