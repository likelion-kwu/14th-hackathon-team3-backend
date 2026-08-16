package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 로컬 디스크 저장소. 개발 환경 기본값이다.
 *
 * <p>⚠️ 저장 위치가 서버 로컬이라 인스턴스가 여러 대이거나 재배포하면 파일을 잃는다.
 * 배포 환경에서는 {@code file.storage=s3} 로 {@link S3FileStorage} 를 쓴다.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file.storage", havingValue = "local", matchIfMissing = true)
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

            return new StoredFile(
                    originalName, file.getSize(), downloadUrl(storedName), storedName);
        } catch (IOException e) {
            log.error("파일 저장에 실패했습니다. fileName={}", originalName, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다.");
        }
    }

    /**
     * 파일명에 공백이나 한글이 있어도 유효한 URL 이 되도록 경로 조각을 인코딩한다.
     * URLEncoder 는 공백을 +로 바꾸는데 경로에서는 %20 이어야 한다.
     */
    private String downloadUrl(String storedName) {
        String encoded = URLEncoder.encode(storedName, StandardCharsets.UTF_8).replace("+", "%20");
        return publicBaseUrl + DOWNLOAD_PATH + encoded;
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

    @Override
    public Long sizeOf(String storedName) {
        Path target = uploadDirectory.resolve(storedName).normalize();

        // 경로 조작으로 업로드 디렉터리 밖을 들여다보는 것을 막는다.
        if (!target.startsWith(uploadDirectory)) {
            return null;
        }
        // 읽을 수 있는 디렉터리도 isReadable 을 통과한다. 크기를 물어볼 대상은 정규 파일뿐이다.
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }

        try {
            return Files.size(target);
        } catch (IOException e) {
            // 파일은 있는데 읽지 못한 경우다. 크기 없음으로 뭉개면 잘못된 첨부가 저장된다.
            log.error("첨부파일 크기를 읽지 못했습니다. file={}", storedName, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "첨부파일 정보를 확인하지 못했습니다.");
        }
    }
}
