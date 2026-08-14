package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.domain.issue.dto.IssueResponse;
import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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

    public Resource download(String storedName) {
        if (storedName == null || storedName.isBlank() || storedName.contains("/") || storedName.contains("\\")) {
            throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "파일명이 올바르지 않습니다.");
        }
        return fileStoragePort.load(storedName);
    }

    /**
     * 저장 파일명은 "UUID_원본이름" 형식이라 앞의 UUID 를 떼어 원래 이름을 돌려준다.
     */
    public String originalNameOf(String storedName) {
        int separator = storedName.indexOf('_');
        return (separator < 0 || separator == storedName.length() - 1)
                ? storedName
                : storedName.substring(separator + 1);
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
