package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.entity.CycleActivity;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.cycle.service.CycleActivityService;
import com.example.likelionhackathon.domain.issue.dto.IssueCommentRequest;
import com.example.likelionhackathon.domain.issue.dto.IssueCommentResponse;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueComment;
import com.example.likelionhackathon.domain.issue.repository.IssueCommentRepository;
import com.example.likelionhackathon.domain.issue.repository.IssueRepository;
import com.example.likelionhackathon.domain.issue.service.IssueMemberPort.MemberProfile;
import com.example.likelionhackathon.domain.project.service.ProjectAccessService;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueCommentService {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final IssueCommentRepository issueCommentRepository;
    private final IssueRepository issueRepository;
    private final CycleRepository cycleRepository;
    private final IssueMemberPort issueMemberPort;
    private final CycleActivityService cycleActivityService;
    private final ProjectAccessService projectAccessService;

    public List<IssueCommentResponse.Item> getComments(Long issueId) {
        Issue issue = findIssue(issueId);
        Long currentMemberId = currentMemberId(issue.getCycleId());

        // 같은 사람이 여러 댓글을 달면 이름 조회가 반복되므로 한 번만 읽는다.
        Map<Long, String> names = new HashMap<>();

        return issueCommentRepository.findByIssueIdOrderByCreatedAtAscIdAsc(issueId).stream()
                .map(comment -> IssueCommentResponse.Item.of(
                        comment,
                        names.computeIfAbsent(comment.getAuthorId(), this::memberName),
                        comment.isWrittenBy(currentMemberId)))
                .toList();
    }

    @Transactional
    public IssueCommentResponse.Created write(Long issueId, IssueCommentRequest.Write request) {
        Issue issue = findIssue(issueId);
        String content = validateContent(request.content());

        MemberProfile author = requireCurrentMember(issue.getCycleId());
        IssueComment saved = issueCommentRepository.save(
                IssueComment.write(issueId, author.userId(), content));

        cycleActivityService.record(CycleActivity.commentAdded(
                issue.getCycleId(),
                LocalDateTime.now(),
                author.name(),
                issue.getId(),
                issue.getTitle(),
                saved.excerpt()
        ));

        return new IssueCommentResponse.Created(saved.getId());
    }

    @Transactional
    public IssueCommentResponse.Item edit(Long commentId, IssueCommentRequest.Edit request) {
        IssueComment comment = findComment(commentId);
        Issue issue = findIssue(comment.getIssueId());
        MemberProfile author = requireAuthor(comment, issue.getCycleId());

        comment.edit(validateContent(request.content()));

        // 수정은 활동 기록을 남기지 않는다. 화면이 남긴 시점만 보여준다.
        return IssueCommentResponse.Item.of(comment, author.name(), true);
    }

    @Transactional
    public void delete(Long commentId) {
        IssueComment comment = findComment(commentId);
        Issue issue = findIssue(comment.getIssueId());
        requireAuthor(comment, issue.getCycleId());

        issueCommentRepository.delete(comment);
    }

    /**
     * 남의 프로젝트 댓글에 손대지 못하도록, 댓글이 달린 이슈의 프로젝트 접근 권한부터 확인한다.
     * commentId 로 들어오는 모든 작업이 이 경로를 지난다.
     */
    private Issue findIssue(Long issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new CustomException(ErrorCode.ISSUE_NOT_FOUND));
        Cycle cycle = cycleRepository.findById(issue.getCycleId())
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND));

        projectAccessService.findProject(cycle.getProjectId());
        projectAccessService.requireAccess(cycle.getProjectId());

        return issue;
    }

    private IssueComment findComment(Long commentId) {
        return issueCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
    }

    /**
     * 프로젝트 접근 권한이 있어도 남이 쓴 댓글은 고칠 수 없다.
     */
    private MemberProfile requireAuthor(IssueComment comment, Long cycleId) {
        MemberProfile current = requireCurrentMember(cycleId);
        if (!comment.isWrittenBy(current.userId())) {
            throw new CustomException(ErrorCode.COMMENT_FORBIDDEN);
        }
        return current;
    }

    private MemberProfile requireCurrentMember(Long cycleId) {
        return issueMemberPort.findCurrentMember(cycleId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.COMMENT_FORBIDDEN, "프로젝트 멤버만 댓글을 남길 수 있습니다."));
    }

    private Long currentMemberId(Long cycleId) {
        return issueMemberPort.findCurrentMember(cycleId)
                .map(MemberProfile::userId)
                .orElse(null);
    }

    private String memberName(Long memberId) {
        return issueMemberPort.findProfile(memberId)
                .map(MemberProfile::name)
                .orElse(null);
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new CustomException(ErrorCode.COMMENT_INVALID_INPUT, "댓글 내용을 입력해주세요.");
        }

        String trimmed = content.strip();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(
                    ErrorCode.COMMENT_INVALID_INPUT, "댓글은 " + MAX_CONTENT_LENGTH + "자를 넘을 수 없습니다.");
        }

        return trimmed;
    }
}
