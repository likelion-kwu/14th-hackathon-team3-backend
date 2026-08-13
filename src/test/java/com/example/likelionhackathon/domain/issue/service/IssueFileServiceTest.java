package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.issue.dto.IssueResponse;
import com.example.likelionhackathon.domain.issue.service.FileStoragePort.StoredFile;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueFileServiceTest {

    @Mock
    private FileStoragePort fileStoragePort;

    private IssueFileService issueFileService;

    @BeforeEach
    void setUp() {
        issueFileService = new IssueFileService(fileStoragePort);
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
    void returnsStoredFileInfo() {
        MultipartFile file = new MockMultipartFile("files", "QA_Result_v2.pdf", null, new byte[]{1, 2, 3});
        when(fileStoragePort.store(file)).thenReturn(
                new StoredFile("QA_Result_v2.pdf", 2516582L, "https://example.com/files/qa_result_v2.pdf"));

        List<IssueResponse.UploadedFile> uploaded = issueFileService.upload(List.of(file));

        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.get(0).fileName()).isEqualTo("QA_Result_v2.pdf");
        assertThat(uploaded.get(0).fileUrl()).isEqualTo("https://example.com/files/qa_result_v2.pdf");
    }
}
