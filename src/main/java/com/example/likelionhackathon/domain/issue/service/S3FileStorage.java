package com.example.likelionhackathon.domain.issue.service;

import com.example.likelionhackathon.global.error.ErrorCode;
import com.example.likelionhackathon.global.error.exception.CustomException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * S3 저장소. {@code file.storage=s3} 일 때만 뜬다.
 *
 * <p>자격증명은 코드나 설정 파일에 두지 않는다. AWS 기본 체인이
 * 환경변수 · 프로파일 · IAM 역할 순으로 찾는다. 배포 환경에서는 IAM 역할을 권장한다.</p>
 *
 * <p>다운로드는 이 서버를 거쳐 내려준다. 프리사인드 URL 을 쓰면 링크를 아는 사람이면
 * 누구나 받을 수 있어, 프로젝트 멤버만 받도록 한 권한 검사가 무력해진다.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file.storage", havingValue = "s3")
public class S3FileStorage implements FileStoragePort {

    /** 다운로드 엔드포인트 경로. IssueController 의 매핑과 맞춰야 한다. */
    private static final String DOWNLOAD_PATH = "/api/v1/issues/files/";

    /** 버킷 안에서 이슈 첨부가 모이는 위치. */
    private static final String KEY_PREFIX = "issues/";

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3FileStorage(
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.region}") String region,
            @Value("${file.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        // 계정 리전 네임스페이스 버킷은 리전 엔드포인트로 접근해야 한다.
        this.s3Client = S3Client.builder().region(Region.of(region)).build();
    }

    @Override
    public StoredFile store(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        // 슬래시가 없는 평평한 키를 쓴다. 다운로드 경로 변수에 그대로 들어가야 하기 때문이다.
        String storedKey = UUID.randomUUID().toString().replace("-", "") + "_" + originalName;

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(KEY_PREFIX + storedKey)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(inputStream, file.getSize())
            );
        } catch (IOException | S3Exception e) {
            log.error("S3 업로드에 실패했습니다. bucket={}, fileName={}", bucket, originalName, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다.");
        }

        return new StoredFile(originalName, file.getSize(), downloadUrl(storedKey), storedKey);
    }

    /**
     * 파일명에 공백이나 한글이 있어도 유효한 URL 이 되도록 경로 조각을 인코딩한다.
     * URLEncoder 는 공백을 +로 바꾸는데 경로에서는 %20 이어야 한다.
     */
    private String downloadUrl(String storedKey) {
        String encoded = URLEncoder.encode(storedKey, StandardCharsets.UTF_8).replace("+", "%20");
        return publicBaseUrl + DOWNLOAD_PATH + encoded;
    }

    @Override
    public Resource load(String storedKey) {
        try {
            return new InputStreamResource(s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(KEY_PREFIX + storedKey).build()));
        } catch (NoSuchKeyException e) {
            throw new CustomException(ErrorCode.ISSUE_NOT_FOUND, "첨부파일을 찾을 수 없습니다.");
        } catch (S3Exception e) {
            log.error("S3 다운로드에 실패했습니다. bucket={}, key={}", bucket, storedKey, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "파일을 가져오지 못했습니다.");
        }
    }

    @PreDestroy
    void closeClient() {
        s3Client.close();
    }
}
