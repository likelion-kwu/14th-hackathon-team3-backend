package com.example.likelionhackathon.domain.conversation.service;

import com.example.likelionhackathon.domain.conversation.dto.ConversationRequest;
import com.example.likelionhackathon.domain.conversation.dto.ConversationResponse;
import com.example.likelionhackathon.domain.conversation.entity.Conversation;
import com.example.likelionhackathon.domain.conversation.entity.Message;
import com.example.likelionhackathon.domain.conversation.repository.ConversationRepository;
import com.example.likelionhackathon.domain.conversation.repository.MessageRepository;
import com.example.likelionhackathon.domain.user.entity.User;
import com.example.likelionhackathon.domain.user.repository.UserRepository;
import com.example.likelionhackathon.domain.workspace.entity.Workspace;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceEnums.WorkspaceMemberStatus;
import com.example.likelionhackathon.domain.workspace.entity.WorkspaceMember;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceMemberRepository;
import com.example.likelionhackathon.domain.workspace.repository.WorkspaceRepository;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import com.example.likelionhackathon.global.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.DateTimeException;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OpenAiTranslationClient translationClient;
    private final OpenAiTemporalExpressionClient temporalExpressionClient;
    private final TemporalCandidateDetector temporalCandidateDetector;
    private final TemporalResolver temporalResolver;

    public ConversationResponse.TranslationPreview previewTranslation(
            Long workspaceId,
            Long conversationId,
            ConversationRequest.TranslationPreview request
    ) {
        ConversationAccess access = requireActiveConversationAccess(workspaceId, conversationId);
        UserPair users = requireUsersAndLanguages(access.currentMember(), access.targetMember());
        Transformation transformed = transform(request.content(), users);
        return new ConversationResponse.TranslationPreview(
                request.content(), transformed.content(), users.target().getLanguage(), transformed.applied(),
                transformed.nuance());
    }

    public ConversationResponse.SentMessage sendMessage(
            Long workspaceId,
            Long conversationId,
            ConversationRequest.SendMessage request
    ) {
        ConversationAccess access = requireActiveConversationAccess(workspaceId, conversationId);
        String translatedContent = null;
        boolean translationApplied = false;

        if (Boolean.TRUE.equals(request.translationUsed())) {
            UserPair users = requireUsersAndLanguages(access.currentMember(), access.targetMember());
            Transformation transformed = transform(request.originalContent(), users);
            translatedContent = transformed.applied() ? transformed.content() : null;
            translationApplied = transformed.applied();
        }

        Message message = messageRepository.saveAndFlush(Message.create(
                access.conversation(),
                access.currentMember().getId(),
                request.originalContent(),
                translatedContent,
                translationApplied
        ));
        return new ConversationResponse.SentMessage(
                message.getId(),
                access.conversation().getId(),
                message.getSenderMemberId(),
                message.getOriginalContent(),
                message.getTranslatedContent(),
                message.isTranslationUsed(),
                message.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public ConversationResponse.Messages getMessages(
            Long workspaceId, Long conversationId, int page, int size) {
        validatePaging(page, size);
        WorkspaceMember currentMember = requireActiveCurrentMember(workspaceId);
        Conversation conversation = conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (!conversation.hasParticipant(currentMember.getId())) {
            throw new CustomException(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }

        Slice<Message> messageSlice = messageRepository
                .findByConversationIdOrderByCreatedAtDescIdDesc(
                        conversationId, PageRequest.of(page, size));
        Map<Long, WorkspaceMember> sendersById = findMembersById(
                messageSlice.getContent().stream().map(Message::getSenderMemberId).collect(Collectors.toSet()));

        List<ConversationResponse.MessageItem> messages = messageSlice.getContent().stream()
                .map(message -> new ConversationResponse.MessageItem(
                        message.getId(),
                        message.getSenderMemberId(),
                        senderName(sendersById.get(message.getSenderMemberId())),
                        message.getOriginalContent(),
                        message.getTranslatedContent(),
                        message.isTranslationUsed(),
                        message.getCreatedAt()
                ))
                .toList();

        return new ConversationResponse.Messages(
                conversationId, messages, page, size, messageSlice.hasNext());
    }

    @Transactional(readOnly = true)
    public ConversationResponse.RecentConversations getRecentConversations(Long workspaceId) {
        WorkspaceMember currentMember = requireActiveCurrentMember(workspaceId);
        List<ConversationRepository.RecentConversationProjection> projections =
                conversationRepository.findRecentConversations(workspaceId, currentMember.getId());

        Map<Long, WorkspaceMember> targetsById = findMembersById(projections.stream()
                .map(projection -> projection.getConversation().getOtherMemberId(currentMember.getId()))
                .collect(Collectors.toSet()));
        Map<Long, User> usersById = findUsersByPrincipalKey(targetsById.values());

        List<ConversationResponse.RecentConversation> conversations = projections.stream()
                .map(projection -> toRecentConversation(
                        projection, currentMember.getId(), targetsById, usersById))
                .toList();
        return new ConversationResponse.RecentConversations(conversations);
    }

    public ConversationResponse.DirectConversation findOrCreateDirectConversation(
            Long workspaceId, ConversationRequest.DirectConversation request) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSPACE_NOT_FOUND));
        WorkspaceMember currentMember = requireActiveCurrentMember(workspaceId);
        WorkspaceMember targetMember = requireActiveTargetMember(workspaceId, request.targetMemberId());
        if (currentMember.getId().equals(targetMember.getId())) {
            throw new CustomException(ErrorCode.SELF_CONVERSATION_NOT_ALLOWED);
        }

        User targetUser = findTargetUser(targetMember);
        Conversation candidate = Conversation.create(workspace, currentMember.getId(), targetMember.getId());
        ConversationResult result = findOrCreate(candidate);
        return new ConversationResponse.DirectConversation(
                result.conversation().getId(), result.created(),
                new ConversationResponse.TargetMember(
                        targetMember.getId(), targetMember.getName(), targetMember.getCompanyName(),
                        targetMember.getTeamName(), targetMember.getJobTitle(), targetUser.getActivityStatus())
        );
    }

    private WorkspaceMember requireActiveCurrentMember(Long workspaceId) {
        return workspaceMemberRepository
                .findByWorkspaceIdAndPrincipalKey(workspaceId, currentUserProvider.currentPrincipalKey())
                .filter(member -> member.getStatus() == WorkspaceMemberStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSPACE_ACCESS_DENIED));
    }

    private WorkspaceMember requireActiveTargetMember(Long workspaceId, Long targetMemberId) {
        return workspaceMemberRepository.findByIdAndWorkspaceId(targetMemberId, workspaceId)
                .filter(member -> member.getStatus() == WorkspaceMemberStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private ConversationAccess requireActiveConversationAccess(Long workspaceId, Long conversationId) {
        WorkspaceMember currentMember = requireActiveCurrentMember(workspaceId);
        Conversation conversation = conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (!conversation.hasParticipant(currentMember.getId())) {
            throw new CustomException(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }
        Long targetMemberId = conversation.getOtherMemberId(currentMember.getId());
        WorkspaceMember targetMember = requireActiveTargetMember(workspaceId, targetMemberId);
        return new ConversationAccess(conversation, currentMember, targetMember);
    }

    private UserPair requireUsersAndLanguages(WorkspaceMember currentMember, WorkspaceMember targetMember) {
        User currentUser = findUserByPrincipalKey(currentMember.getPrincipalKey());
        User targetUser = findUserByPrincipalKey(targetMember.getPrincipalKey());
        if (currentUser.getLanguage() == null || targetUser.getLanguage() == null) {
            throw new CustomException(ErrorCode.TRANSLATION_LANGUAGE_NOT_CONFIGURED);
        }
        return new UserPair(currentUser, targetUser);
    }

    private Transformation transform(String content, UserPair users) {
        String sourceLanguage = users.sender().getLanguage();
        String targetLanguage = users.target().getLanguage();
        if (!temporalCandidateDetector.mayContainTemporalExpression(content)) {
            return translateOrShortcut(content, sourceLanguage, targetLanguage, null);
        }

        TemporalModels.TemporalExtraction extraction = temporalExpressionClient.extract(content, sourceLanguage);
        if (!extraction.hasTemporalExpression()) {
            return translateOrShortcut(content, sourceLanguage, targetLanguage, null);
        }

        ZoneId senderZone = requireZone(users.sender().getTimezone());
        ZoneId receiverZone = requireZone(users.target().getTimezone());
        TemporalModels.ResolvedTemporalContext context;
        try {
            context = temporalResolver.resolve(extraction.expressions(), senderZone, receiverZone);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new CustomException(ErrorCode.AI_TRANSLATION_FAILED);
        }
        return translateOrShortcut(content, sourceLanguage, targetLanguage, context);
    }

    private Transformation translateOrShortcut(String content, String sourceLanguage, String targetLanguage,
                                                TemporalModels.ResolvedTemporalContext context) {
        if (context == null && sourceLanguage.equals(targetLanguage)) {
            return new Transformation(content, null, false);
        }
        OpenAiTranslationClient.TranslationResult translated = context == null
                ? translationClient.translate(content, sourceLanguage, targetLanguage)
                : translationClient.translate(content, sourceLanguage, targetLanguage, context);
        return new Transformation(translated.translatedContent(), translated.nuance(), true);
    }

    private ZoneId requireZone(String timezone) {
        if (timezone == null) throw new CustomException(ErrorCode.TEMPORAL_CONTEXT_NOT_CONFIGURED);
        try { return ZoneId.of(timezone); }
        catch (DateTimeException exception) {
            throw new CustomException(ErrorCode.TEMPORAL_CONTEXT_NOT_CONFIGURED);
        }
    }

    private User findUserByPrincipalKey(String principalKey) {
        try {
            return userRepository.findById(Long.valueOf(principalKey))
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private User findTargetUser(WorkspaceMember targetMember) {
        try {
            return userRepository.findById(Long.valueOf(targetMember.getPrincipalKey()))
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private ConversationResponse.RecentConversation toRecentConversation(
            ConversationRepository.RecentConversationProjection projection,
            Long currentMemberId,
            Map<Long, WorkspaceMember> targetsById,
            Map<Long, User> usersById
    ) {
        Conversation conversation = projection.getConversation();
        Long targetMemberId = conversation.getOtherMemberId(currentMemberId);
        WorkspaceMember targetMember = targetsById.get(targetMemberId);
        User targetUser = targetMember == null ? null : userForMember(targetMember, usersById);
        return new ConversationResponse.RecentConversation(
                conversation.getId(),
                targetMemberId,
                targetMember == null ? null : targetMember.getName(),
                targetMember == null ? null : targetMember.getCompanyName(),
                targetMember == null ? null : targetMember.getTeamName(),
                targetMember == null ? null : targetMember.getJobTitle(),
                targetUser == null ? null : targetUser.getActivityStatus(),
                projection.getLastMessageAt()
        );
    }

    private Map<Long, WorkspaceMember> findMembersById(Set<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return workspaceMemberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(WorkspaceMember::getId, Function.identity()));
    }

    private Map<Long, User> findUsersByPrincipalKey(Iterable<WorkspaceMember> members) {
        Set<Long> userIds = new java.util.LinkedHashSet<>();
        for (WorkspaceMember member : members) {
            parseUserId(member.getPrincipalKey()).ifPresent(userIds::add);
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private User userForMember(WorkspaceMember member, Map<Long, User> usersById) {
        return parseUserId(member.getPrincipalKey()).map(usersById::get).orElse(null);
    }

    private java.util.Optional<Long> parseUserId(String principalKey) {
        try {
            return java.util.Optional.of(Long.valueOf(principalKey));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    private String senderName(WorkspaceMember sender) {
        return sender == null ? null : sender.getName();
    }

    private void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "페이지 번호 또는 크기가 올바르지 않습니다."
            );
        }
    }

    private ConversationResult findOrCreate(Conversation candidate) {
        return conversationRepository.findByWorkspaceIdAndMemberLowIdAndMemberHighId(
                        candidate.getWorkspace().getId(), candidate.getMemberLowId(), candidate.getMemberHighId())
                .map(conversation -> new ConversationResult(conversation, false))
                .orElseGet(() -> saveOrFindAfterConflict(candidate));
    }

    private ConversationResult saveOrFindAfterConflict(Conversation candidate) {
        try {
            return new ConversationResult(conversationRepository.saveAndFlush(candidate), true);
        } catch (DataIntegrityViolationException exception) {
            return conversationRepository.findByWorkspaceIdAndMemberLowIdAndMemberHighId(
                            candidate.getWorkspace().getId(), candidate.getMemberLowId(), candidate.getMemberHighId())
                    .map(conversation -> new ConversationResult(conversation, false))
                    .orElseThrow(() -> exception);
        }
    }

    private record ConversationResult(Conversation conversation, boolean created) {
    }

    private record ConversationAccess(
            Conversation conversation,
            WorkspaceMember currentMember,
            WorkspaceMember targetMember
    ) {
    }

    private record UserPair(User sender, User target) {
    }

    private record Transformation(String content, String nuance, boolean applied) {
    }
}
