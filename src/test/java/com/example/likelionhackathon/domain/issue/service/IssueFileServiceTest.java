package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.cycle.entity.Cycle;
import com.example.likelionhackathon.domain.cycle.repository.CycleRepository;
import com.example.likelionhackathon.domain.issue.dto.IssueResponse;
import com.example.likelionhackathon.domain.issue.entity.Issue;
import com.example.likelionhackathon.domain.issue.entity.IssueAttachment;
import com.example.likelionhackathon.domain.issue.entity.IssueEnums.IssuePriority;
import com.example.likelionhackathon.domain.issue.repository.IssueAttachmentRepository;
import com.example.likelionhackathon.domain.issue.service.FileStoragePort.StoredFile;
import com.example.likelionhackathon.domain.project.service.ProjectAccessService;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

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
class IssueFileServiceTest {

    private static final String STORED_NAME = "dccaa16257b34b1f9fc1787af48d7bd5_QA_Result_v2.pdf";
    private static final Long PROJECT_ID = 1L;
    private static final Long CYCLE_ID = 3L;

    @Mock
    private FileStoragePort fileStoragePort;

    @Mock
    private IssueAttachmentRepository issueAttachmentRepository;

    @Mock
    private CycleRepository cycleRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    private IssueFileService issueFileService;

    @BeforeEach
    void setUp() {
        issueFileService = new IssueFileService(
                fileStoragePort, issueAttachmentRepository, cycleRepository, projectAccessService);
    }

    @Test
    void downloadRejectsNonProjectMemberForAttachedFile() {
        when(issueAttachmentRepository.findByStoredKey(STORED_NAME))
                .thenReturn(List.of(attachment()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));
        doThrow(new CustomException(ErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireAccess(PROJECT_ID);

        assertThatThrownBy(() -> issueFileService.download(STORED_NAME))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);

        verify(fileStoragePort, never()).load(any());
    }

    @Test
    void downloadAllowsProjectMemberForAttachedFile() {
        when(issueAttachmentRepository.findByStoredKey(STORED_NAME))
                .thenReturn(List.of(attachment()));
        when(cycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle()));

        issueFileService.download(STORED_NAME);

        verify(fileStoragePort).load(STORED_NAME);
    }

    @Test
    void downloadAllowsFileNotYetAttachedToAnyIssue() {
        when(issueAttachmentRepository.findByStoredKey(STORED_NAME)).thenReturn(List.of());

        issueFileService.download(STORED_NAME);

        verify(fileStoragePort).load(STORED_NAME);
    }

    private IssueAttachment attachment() {
        IssueAttachment attachment = new IssueAttachment(
                "QA_Result_v2.pdf", 100L, "http://localhost:8080/api/v1/issues/files/" + STORED_NAME, STORED_NAME);
        Issue issue = Issue.create(
                CYCLE_ID, "제목", "설명", IssuePriority.HIGH, 1L, LocalDate.of(2026, 8, 6));
        issue.replaceAttachments(List.of(attachment));
        return attachment;
    }

    private Cycle cycle() {
        Cycle cycle = Cycle.create(
                PROJECT_ID, "Cycle 3", LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 12), null);
        ReflectionTestUtils.setField(cycle, "id", CYCLE_ID);
        return cycle;
    }

    @Test
    void rejectsDisallowedExtension() {
        MultipartFile file = new MockMultipartFile("files", "malware.exe", null, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> issueFileService.upload(List.of(file)))
                .isInstanceOf(CustomException.class)
                .hasMessage("허용되지 않은 파일 형식입니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_INVALID_INPUT);

        verify(fileStoragePort, never()).store(any());
    }

    @Test
    void rejectsFileOverTwentyMegabytes() {
        byte[] oversized = new byte[20 * 1024 * 1024 + 1];
        MultipartFile file = new MockMultipartFile("files", "big.pdf", null, oversized);

        assertThatThrownBy(() -> issueFileService.upload(List.of(file)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_FILE_TOO_LARGE);
    }

    @Test
    void rejectsMoreThanFiveFiles() {
        MultipartFile file = new MockMultipartFile("files", "doc.pdf", null, new byte[]{1});
        List<MultipartFile> six = List.of(file, file, file, file, file, file);

        assertThatThrownBy(() -> issueFileService.upload(six))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_INVALID_INPUT);
    }

    @Test
    void downloadRejectsPathTraversal() {
        assertThatThrownBy(() -> issueFileService.download("../../etc/passwd"))
                .isInstanceOf(CustomException.class)
                .hasMessage("파일명이 올바르지 않습니다.")
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ISSUE_INVALID_INPUT);

        verify(fileStoragePort, never()).load(any());
    }

    @Test
    void originalNameStripsStoredPrefix() {
        assertThat(issueFileService.originalNameOf("14a4b22d20f740d7946859e77b990403_QA_Result_v2.pdf"))
                .isEqualTo("QA_Result_v2.pdf");
        assertThat(issueFileService.originalNameOf("접두사없는파일.pdf"))
                .isEqualTo("접두사없는파일.pdf");
    }

    @Test
    void returnsStoredFileInfo() {
        MultipartFile file = new MockMultipartFile("files", "QA_Result_v2.pdf", null, new byte[]{1, 2, 3});
        when(fileStoragePort.store(file)).thenReturn(
                new StoredFile("QA_Result_v2.pdf", 2516582L, "https://example.com/files/qa_result_v2.pdf", "key1"));

        List<IssueResponse.UploadedFile> uploaded = issueFileService.upload(List.of(file));

        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.get(0).fileName()).isEqualTo("QA_Result_v2.pdf");
        assertThat(uploaded.get(0).fileUrl()).isEqualTo("https://example.com/files/qa_result_v2.pdf");
    }
}
