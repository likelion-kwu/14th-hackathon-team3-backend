package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.cycle.service.CycleActivityService;
import com.example.likelionhackathon.domain.cycle.service.CycleIssuePort;
import com.example.likelionhackathon.domain.issue.dto.IssueRequest;
import com.example.likelionhackathon.domain.issue.dto.IssueResponse;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueAttachment;
import com.example.likelionhackathon.domain.issue.entity.IssueChecklistItem;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssueStatus;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import com.example.likelionhackathon.domain.issue.service.IssueMemberPort.MemberProfile;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "dueDate", "priority", "title", "status");

    private final IssueRepository issueRepository;
    private final CycleRepository cycleRepository;
    private final IssueMemberPort issueMemberPort;
    private final CycleIssuePort cycleIssuePort;
    private final CycleActivityService cycleActivityService;

    public List<IssueResponse.Summary> getIssues(
            Long cycleId,
            List<String> status,
            String priority,
            Long assigneeId,
            String keyword,
            String sort,
            int page,
            int size
    ) {
        if (!cycleRepository.existsById(cycleId)) {
            throw new CustomException(ErrorCode.CYCLE_NOT_FOUND);
        }

        List<IssueStatus> statuses = parseStatuses(status);
        IssuePriority parsedPriority = parsePriority(priority);
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));

        Specification<Issue> spec = buildSpecification(cycleId, statuses, parsedPriority, assigneeId, keyword);

        return issueRepository.findAll(spec, pageable).getContent().stream()
                .map(issue -> IssueResponse.Summary.of(issue, assigneeName(issue.getAssigneeId())))
                .toList();
    }

    @Transactional
    public IssueResponse.Created create(IssueRequest.Create request) {
        validateRequired(
                request.cycleId(), request.title(), request.priority(),
                request.description(), request.assigneeId(), request.dueDate());

        Cycle cycle = findCycle(request.cycleId());
        validateDueDateWithinCycle(cycle, request.dueDate());
        validateAssignee(request.cycleId(), request.assigneeId());

        Issue issue = Issue.create(
                request.cycleId(),
                request.title(),
                request.description(),
                request.priority(),
                request.assigneeId(),
                request.dueDate()
        );

        List<String> checklist = request.safeChecklist();
        for (int i = 0; i < checklist.size(); i++) {
            issue.addChecklistItem(new IssueChecklistItem(checklist.get(i), false, i));
        }
        issue.replaceAttachments(toAttachments(request.safeAttachments()));

        return new IssueResponse.Created(issueRepository.save(issue).getId());
    }

    public IssueResponse.Detail getDetail(Long issueId) {
        Issue issue = findIssue(issueId);
        String cycleName = cycleRepository.findById(issue.getCycleId())
                .map(Cycle::getName)
                .orElse(null);

        MemberProfile profile = issueMemberPort.findProfile(issue.getAssigneeId())
                .orElse(new MemberProfile(issue.getAssigneeId(), null, null, null, null));

        return new IssueResponse.Detail(
                issue.getId(),
                issue.getCycleId(),
                cycleName,
                issue.getTitle(),
                issue.getStatus(),
                issue.getPriority(),
                issue.getDescription(),
                IssueResponse.Assignee.of(profile),
                issue.getDueDate(),
                issue.checklistDoneCount(),
                issue.checklistTotalCount(),
                issue.getChecklist().stream().map(IssueResponse.ChecklistItem::of).toList(),
                issue.getAttachments().stream().map(IssueResponse.Attachment::of).toList()
        );
    }

    @Transactional
    public IssueResponse.Updated update(Long issueId, IssueRequest.Update request) {
        Issue issue = findIssue(issueId);
        if (issue.isClosed()) {
            throw new CustomException(ErrorCode.ISSUE_CONFLICT, "완료 또는 취소된 이슈는 수정할 수 없습니다.");
        }

        validateRequired(
                request.cycleId(), request.title(), request.priority(),
                request.description(), request.assigneeId(), request.dueDate());

        Cycle cycle = findCycle(request.cycleId());
        validateDueDateWithinCycle(cycle, request.dueDate());
        validateAssignee(request.cycleId(), request.assigneeId());

        issue.update(
                request.cycleId(),
                request.title(),
                request.description(),
                request.priority(),
                request.assigneeId(),
                request.dueDate()
        );

        replaceChecklist(issue, request.safeChecklist());
        issue.replaceAttachments(toAttachments(request.safeAttachments()));

        return IssueResponse.Updated.of(issue);
    }

    @Transactional
    public IssueResponse.StatusChanged changeStatus(Long issueId, IssueRequest.ChangeStatus request) {
        if (request == null || request.status() == null) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "변경할 상태를 입력해주세요.");
        }

        Issue issue = findIssue(issueId);
        IssueStatus previousStatus = issue.getStatus();

        if (!previousStatus.canTransitionTo(request.status())) {
            throw new CustomException(ErrorCode.ISSUE_CONFLICT, "허용되지 않은 상태 변경입니다.");
        }
        if (request.status() == IssueStatus.DONE && !issue.isAllChecklistDone()) {
            throw new CustomException(ErrorCode.ISSUE_CONFLICT, "완료 조건이 모두 충족되지 않았습니다.");
        }

        issue.changeStatus(request.status());

        cycleActivityService.record(CycleActivity.issueStatusChanged(
                issue.getCycleId(),
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                assigneeNameOrFallback(issue.getAssigneeId()),
                issue.getId(),
                issue.getTitle(),
                previousStatus.name(),
                request.status().name()
        ));

        return new IssueResponse.StatusChanged(
                issue.getId(),
                previousStatus,
                issue.getStatus(),
                cycleIssuePort.statsOf(issue.getCycleId()).progressRate()
        );
    }

    @Transactional
    public IssueResponse.ChecklistChecked checkItem(Long issueId, Long itemId, IssueRequest.CheckItem request) {
        if (request == null || request.isDone() == null) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "체크 여부를 입력해주세요.");
        }

        Issue issue = findIssue(issueId);
        IssueChecklistItem item = issue.findChecklistItem(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.ISSUE_NOT_FOUND, "존재하지 않는 완료 조건입니다."));

        item.changeDone(request.isDone());

        return new IssueResponse.ChecklistChecked(
                item.getId(),
                item.isDone(),
                issue.checklistDoneCount(),
                issue.checklistTotalCount()
        );
    }

    @Transactional
    public void delete(Long issueId) {
        issueRepository.delete(findIssue(issueId));
    }

    private void replaceChecklist(Issue issue, List<IssueRequest.ChecklistItem> requested) {
        Set<Long> retainedIds = requested.stream()
                .map(IssueRequest.ChecklistItem::itemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        issue.removeChecklistItemsNotIn(retainedIds);

        for (int i = 0; i < requested.size(); i++) {
            IssueRequest.ChecklistItem requestedItem = requested.get(i);
            if (requestedItem.content() == null || requestedItem.content().isBlank()) {
                throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "완료 조건 내용을 입력해주세요.");
            }

            if (requestedItem.itemId() == null) {
                issue.addChecklistItem(new IssueChecklistItem(requestedItem.content(), requestedItem.done(), i));
                continue;
            }

            int orderIndex = i;
            issue.findChecklistItem(requestedItem.itemId())
                    .orElseThrow(() -> new CustomException(ErrorCode.ISSUE_NOT_FOUND, "존재하지 않는 완료 조건입니다."))
                    .update(requestedItem.content(), requestedItem.done(), orderIndex);
        }
    }

    private List<IssueAttachment> toAttachments(List<String> fileUrls) {
        List<IssueAttachment> attachments = new ArrayList<>();
        for (String fileUrl : fileUrls) {
            if (fileUrl == null || fileUrl.isBlank()) {
                throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "첨부파일 URL이 올바르지 않습니다.");
            }
            attachments.add(new IssueAttachment(extractFileName(fileUrl), null, fileUrl));
        }
        return attachments;
    }

    private String extractFileName(String fileUrl) {
        int lastSlash = fileUrl.lastIndexOf('/');
        String fileName = (lastSlash < 0) ? fileUrl : fileUrl.substring(lastSlash + 1);
        return fileName.isBlank() ? fileUrl : fileName;
    }

    private Specification<Issue> buildSpecification(
            Long cycleId,
            List<IssueStatus> statuses,
            IssuePriority priority,
            Long assigneeId,
            String keyword
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("cycleId"), cycleId));

            if (!statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (priority != null) {
                predicates.add(builder.equal(root.get("priority"), priority));
            }
            if (assigneeId != null) {
                predicates.add(builder.equal(root.get("assigneeId"), assigneeId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)
                ));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<IssueStatus> parseStatuses(List<String> status) {
        if (status == null || status.isEmpty()) {
            return List.of();
        }
        try {
            return status.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> IssueStatus.valueOf(value.toUpperCase()))
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "유효하지 않은 필터 값입니다.");
        }
    }

    private IssuePriority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        try {
            return IssuePriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "유효하지 않은 필터 값입니다.");
        }
    }

    // "createdAt,desc" 형식을 Sort 로 바꾼다.
    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "유효하지 않은 필터 값입니다.");
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1) {
            direction = "asc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
        return Sort.by(direction, field);
    }

    private void validateRequired(
            Long cycleId,
            String title,
            IssuePriority priority,
            String description,
            Long assigneeId,
            LocalDate dueDate
    ) {
        boolean missing = cycleId == null
                || title == null || title.isBlank()
                || priority == null
                || description == null || description.isBlank()
                || assigneeId == null
                || dueDate == null;

        if (missing) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "필수 입력값이 누락되었습니다.");
        }
    }

    private void validateDueDateWithinCycle(Cycle cycle, LocalDate dueDate) {
        if (dueDate.isBefore(cycle.getStartDate()) || dueDate.isAfter(cycle.getEndDate())) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "처리 일자가 사이클 기간을 벗어났습니다.");
        }
    }

    private void validateAssignee(Long cycleId, Long assigneeId) {
        if (!issueMemberPort.isProjectMember(cycleId, assigneeId)) {
            throw new CustomException(ErrorCode.ISSUE_NOT_FOUND, "담당자가 프로젝트 멤버가 아닙니다.");
        }
    }

    private String assigneeName(Long assigneeId) {
        return issueMemberPort.findProfile(assigneeId)
                .map(MemberProfile::name)
                .orElse(null);
    }

    private String assigneeNameOrFallback(Long assigneeId) {
        String name = assigneeName(assigneeId);
        return (name == null || name.isBlank()) ? "담당자" : name;
    }

    private Cycle findCycle(Long cycleId) {
        return cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND));
    }

    private Issue findIssue(Long issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new CustomException(ErrorCode.ISSUE_NOT_FOUND));
    }
}
