package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * S3 연결 전까지 쓰는 로컬 디스크 저장소.
 *
 * <p>⚠️ 저장 위치가 서버 로컬이라 인스턴스가 여러 대면 파일을 못 찾는다.
 * 배포 전에 S3 구현으로 교체해야 한다.</p>
 */
@Slf4j
@Service
public class LocalFileStorage implements FileStoragePort {

    /** 다운로드 엔드포인트 경로. IssueController 의 매핑과 맞춰야 한다. */
    private static final String DOWNLOAD_PATH = "/api/v1/issues/files/";

    private final Path uploadDirectory;
    private final String publicBaseUrl;

    public LocalFileStorage(
            @Value("${file.upload-dir:uploads}") String uploadDir,
            @Value("${file.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    public StoredFile store(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + originalName;

        try {
            Files.createDirectories(uploadDirectory);
            Path target = uploadDirectory.resolve(storedName).normalize();

            // 파일명에 상위 경로가 섞여 업로드 디렉터리 밖으로 나가는 것을 막는다.
            if (!target.startsWith(uploadDirectory)) {
                throw new CustomException(ErrorCode.ISSUE_INVALID_INPUT, "파일명이 올바르지 않습니다.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredFile(originalName, file.getSize(), publicBaseUrl + DOWNLOAD_PATH + storedName);
        } catch (IOException e) {
            log.error("파일 저장에 실패했습니다. fileName={}", originalName, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다.");
        }
    }

    @Override
    public Resource load(String storedName) {
        Path target = uploadDirectory.resolve(storedName).normalize();

        // 경로 조작으로 업로드 디렉터리 밖 파일을 읽는 것을 막는다.
        if (!target.startsWith(uploadDirectory) || !Files.isReadable(target)) {
            throw new CustomException(ErrorCode.ISSUE_NOT_FOUND, "첨부파일을 찾을 수 없습니다.");
        }

        return new FileSystemResource(target);
    }
}
