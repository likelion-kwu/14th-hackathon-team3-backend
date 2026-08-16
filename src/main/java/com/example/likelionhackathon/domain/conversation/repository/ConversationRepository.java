package com.example.likelionhackathon.domain.conversation.repository;

import com.example.likelionhackathon.domain.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByWorkspaceIdAndMemberLowIdAndMemberHighId(
            Long workspaceId, Long memberLowId, Long memberHighId);

    Optional<Conversation> findByIdAndWorkspaceId(Long conversationId, Long workspaceId);

    @Query("""
            select conversation as conversation, max(message.createdAt) as lastMessageAt
            from Message message
            join message.conversation conversation
            where conversation.workspace.id = :workspaceId
              and (conversation.memberLowId = :memberId or conversation.memberHighId = :memberId)
            group by conversation
            order by max(message.createdAt) desc, conversation.id desc
            """)
    List<RecentConversationProjection> findRecentConversations(
            @Param("workspaceId") Long workspaceId,
            @Param("memberId") Long memberId
    );

    interface RecentConversationProjection {
        Conversation getConversation();

        LocalDateTime getLastMessageAt();
    }
}
