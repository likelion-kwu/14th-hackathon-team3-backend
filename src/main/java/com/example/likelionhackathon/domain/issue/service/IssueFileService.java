package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.issue.dto.IssueResponse;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IssueFileService {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "png", "jpg", "jpeg", "docx", "xlsx", "pptx", "zip");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 5;

    private final FileStoragePort fileStoragePort;

    public List<IssueResponse.UploadedFile> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "업로드할 파일을 선택해주세요.");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT,
                    "한 번에 최대 %d개까지 업로드할 수 있습니다.".formatted(MAX_FILE_COUNT));
        }

        files.forEach(this::validate);

        return files.stream()
                .map(fileStoragePort::store)
                .map(stored -> new IssueResponse.UploadedFile(
                        stored.fileName(), stored.fileSize(), stored.fileUrl()))
                .toList();
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "빈 파일은 업로드할 수 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.ISSUE_FILE_TOO_LARGE);
        }
        if (!ALLOWED_EXTENSIONS.contains(extensionOf(file.getOriginalFilename()))) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "허용되지 않은 파일 형식입니다.");
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
