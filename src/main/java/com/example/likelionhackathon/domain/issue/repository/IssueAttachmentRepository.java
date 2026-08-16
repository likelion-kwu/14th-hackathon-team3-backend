package com.example.likelionhackathon.domain.issue.repository;

import com.example.likelionhackathon.domain.issue.entity.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, Long> {

    /**
     * 저장 키로 첨부를 찾는다. 다운로드 권한을 확인할 때 쓴다.
     */
    List<IssueAttachment> findByStoredKey(String storedKey);
}
