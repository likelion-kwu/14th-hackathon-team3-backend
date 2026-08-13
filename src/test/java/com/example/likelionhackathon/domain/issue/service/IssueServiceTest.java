package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.cycle.service.CycleActivityService;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort.IssueStats;
import com.example.likelionhackathon.domain.issue.dto.IssueRequest;
import com.example.likelionhackathon.domain.issue.dto.IssueResponse;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueChecklistItem;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    private static final Long CYCLE_ID = 3L;
    private static final Long ISSUE_ID = 12L;
    private static final Long ASSIGNEE_ID = 7L;
    private static final LocalDate CYCLE_START = LocalDate.of(2026, 7, 29);
    private static final LocalDate CYCLE_END = LocalDate.of(2026, 8, 12);

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private CycleRepository cycleRepository;

    @Mock
    private IssueMemberPort issueMemberPort;

    @Mock
    private CycleIssuePort cycleIssuePort;

    @Mock
    private CycleActivityService cycleActivityService;

    private IssueService issueService;

    @BeforeEach
    void setUp() {
        issueService = new IssueService(
                issueRepository, cycleRepository, issueMemberPort, cycleIssuePort, cycleActivityService);
    }

    @Test
    void createRejectsMissingRequiredFields() {
        IssueRequest.Create request = new IssueRequest.Create(
                CYCLE_ID, "  ", IssuePriority.URGENT, "설명", null, ASSIGNEE_ID,
                LocalDate.of(2026, 8, 6), null);

        assertThatThrownBy(() -> issueService.create(request))
                .isInstanceOf(CustomException.class)
                .hasMessage("필수 입력값이 누락되었습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_INVALID_INPUT);

        verify(issueRepository, never()).save(any());
    }

    @Test
    void createRejectsDueDateOutsideCyclePeriod() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));

        IssueRequest.Create request = new IssueRequest.Create(
                CYCLE_ID, "제목", IssuePriority.HIGH, "설명", null, ASSIGNEE_ID,
                LocalDate.of(2026, 9, 1), null);

        assertThatThrownBy(() -> issueService.create(request))
                .isInstanceOf(CustomException.class)
                .hasMessage("처리 일자가 사이클 기간을 벗어났습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_INVALID_INPUT);
    }

    @Test
    void createRejectsNonProjectMemberAssignee() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.isProjectMember(CYCLE_ID, ASSIGNEE_ID)).thenReturn(false);

        IssueRequest.Create request = new IssueRequest.Create(
                CYCLE_ID, "제목", IssuePriority.HIGH, "설명", null, ASSIGNEE_ID,
                LocalDate.of(2026, 8, 6), null);

        assertThatThrownBy(() -> issueService.create(request))
                .isInstanceOf(CustomException.class)
                .hasMessage("담당자가 프로젝트 멤버가 아닙니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    void createStoresChecklistInOrder() {
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.isProjectMember(CYCLE_ID, ASSIGNEE_ID)).thenReturn(true);
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> {
            Issue saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ISSUE_ID);
            return saved;
        });

        IssueRequest.Create request = new IssueRequest.Create(
                CYCLE_ID, "제목", IssuePriority.URGENT, "설명",
                List.of("결제 API 요구사항 확정", "국가별 캠페인 문구 확정"),
                ASSIGNEE_ID, LocalDate.of(2026, 8, 6),
                List.of("https://example.com/files/qa_result_v2.pdf"));

        IssueResponse.Created response = issueService.create(request);

        assertThat(response.issueId()).isEqualTo(ISSUE_ID);
    }

    @Test
    void updateRejectsClosedIssue() {
        Issue issue = issue();
        issue.changeStatus(IssueStatus.DONE);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        IssueRequest.Update request = new IssueRequest.Update(
                CYCLE_ID, "제목", IssuePriority.HIGH, "설명", null, ASSIGNEE_ID,
                LocalDate.of(2026, 8, 6), null);

        assertThatThrownBy(() -> issueService.update(ISSUE_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessage("완료 또는 취소된 이슈는 수정할 수 없습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_CONFLICT);
    }

    @Test
    void updateReplacesChecklistAddingAndRemoving() {
        Issue issue = issue();
        addChecklistItem(issue, 1L, "결제 API 요구사항 확정", true, 0);
        addChecklistItem(issue, 2L, "지워질 항목", false, 1);

        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        when(issueMemberPort.isProjectMember(CYCLE_ID, ASSIGNEE_ID)).thenReturn(true);

        IssueRequest.Update request = new IssueRequest.Update(
                CYCLE_ID, "수정된 제목", IssuePriority.HIGH, "수정된 설명",
                List.of(
                        new IssueRequest.ChecklistItem(1L, "결제 API 요구사항 확정", true),
                        new IssueRequest.ChecklistItem(null, "신규 추가 항목", false)
                ),
                ASSIGNEE_ID, LocalDate.of(2026, 8, 10), null);

        IssueResponse.Updated response = issueService.update(ISSUE_ID, request);

        assertThat(response.checklistTotalCount()).isEqualTo(2);
        assertThat(response.checklistDoneCount()).isEqualTo(1);
        assertThat(issue.getChecklist())
                .extracting(IssueChecklistItem::getContent)
                .containsExactly("결제 API 요구사항 확정", "신규 추가 항목");
    }

    @Test
    void changeStatusRejectsTransitionFromClosedIssue() {
        Issue issue = issue();
        issue.changeStatus(IssueStatus.CANCELED);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() -> issueService.changeStatus(
                ISSUE_ID, new IssueRequest.ChangeStatus(IssueStatus.IN_PROGRESS, null)))
                .isInstanceOf(CustomException.class)
                .hasMessage("허용되지 않은 상태 변경입니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_CONFLICT);
    }

    @Test
    void changeStatusRejectsDoneWhenChecklistIncomplete() {
        Issue issue = issue();
        issue.changeStatus(IssueStatus.IN_PROGRESS);
        addChecklistItem(issue, 1L, "미완료 항목", false, 0);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() -> issueService.changeStatus(
                ISSUE_ID, new IssueRequest.ChangeStatus(IssueStatus.DONE, null)))
                .isInstanceOf(CustomException.class)
                .hasMessage("완료 조건이 모두 충족되지 않았습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_CONFLICT);
    }

    @Test
    void changeStatusRecordsActivityAndReturnsCycleProgress() {
        Issue issue = issue();
        issue.changeStatus(IssueStatus.IN_PROGRESS);
        addChecklistItem(issue, 1L, "완료된 항목", true, 0);

        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        lenient().when(issueMemberPort.findProfile(anyLong())).thenReturn(Optional.empty());
        when(cycleIssuePort.statsOf(CYCLE_ID)).thenReturn(new IssueStats(11, 9, 2, 0, 0));

        IssueResponse.StatusChanged response = issueService.changeStatus(
                ISSUE_ID, new IssueRequest.ChangeStatus(IssueStatus.DONE, "마케팅팀 최종 승인 완료"));

        assertThat(response.previousStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
        assertThat(response.status()).isEqualTo(IssueStatus.DONE);
        assertThat(response.cycleProgressRate()).isEqualTo(82); // 9 / 11
        verify(cycleActivityService).record(any());
    }

    @Test
    void checkItemUpdatesDoneCount() {
        Issue issue = issue();
        addChecklistItem(issue, 1L, "항목 1", true, 0);
        addChecklistItem(issue, 2L, "항목 2", false, 1);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        IssueResponse.ChecklistChecked response =
                issueService.checkItem(ISSUE_ID, 2L, new IssueRequest.CheckItem(true));

        assertThat(response.itemId()).isEqualTo(2L);
        assertThat(response.isDone()).isTrue();
        assertThat(response.checklistDoneCount()).isEqualTo(2);
        assertThat(response.checklistTotalCount()).isEqualTo(2);
    }

    @Test
    void checkItemReturns404WhenItemMissing() {
        Issue issue = issue();
        addChecklistItem(issue, 1L, "항목 1", false, 0);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() -> issueService.checkItem(ISSUE_ID, 99L, new IssueRequest.CheckItem(true)))
                .isInstanceOf(CustomException.class)
                .hasMessage("존재하지 않는 완료 조건입니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    void getIssuesRejectsUnknownStatusFilter() {
        when(cycleRepository.existsById(CYCLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> issueService.getIssues(
                CYCLE_ID, List.of("NOT_A_STATUS"), null, null, null, null, 0, 20))
                .isInstanceOf(CustomException.class)
                .hasMessage("유효하지 않은 필터 값입니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_INVALID_INPUT);
    }

    @Test
    void getIssuesRejectsUnknownSortField() {
        when(cycleRepository.existsById(CYCLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> issueService.getIssues(
                CYCLE_ID, null, null, null, null, "dropTable,desc", 0, 20))
                .isInstanceOf(CustomException.class)
                .hasMessage("유효하지 않은 필터 값입니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_INVALID_INPUT);
    }

    private Cycle cycle() {
        Cycle cycle = Cycle.create(1L, "Cycle 3", CYCLE_START, CYCLE_END, null);
        ReflectionTestUtils.setField(cycle, "id", CYCLE_ID);
        return cycle;
    }

    private Issue issue() {
        Issue issue = Issue.create(
                CYCLE_ID, "제목", "설명", IssuePriority.URGENT, ASSIGNEE_ID, LocalDate.of(2026, 8, 6));
        ReflectionTestUtils.setField(issue, "id", ISSUE_ID);
        return issue;
    }

    private void addChecklistItem(Issue issue, Long itemId, String content, boolean done, int orderIndex) {
        IssueChecklistItem item = new IssueChecklistItem(content, done, orderIndex);
        ReflectionTestUtils.setField(item, "id", itemId);
        issue.addChecklistItem(item);
    }
}
