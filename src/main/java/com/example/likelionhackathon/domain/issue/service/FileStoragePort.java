package com.example.likelionhackathon.domain.issue.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일 저장소.
 *
 * <p>{@code file.storage} 설정으로 구현체를 고른다.
 * {@code local} 이면 {@link LocalFileStorage}, {@code s3} 면 {@link S3FileStorage} 다.
 * 어느 쪽이든 서비스 코드는 바뀌지 않는다.</p>
 */
public interface FileStoragePort {

    StoredFile store(MultipartFile file);

    /**
     * 저장된 파일을 읽어온다. 다운로드 엔드포인트가 사용한다.
     *
     * @param storedKey 슬래시가 없는 평평한 키. 저장소가 자기 방식대로 위치를 결정한다.
     */
    Resource load(String storedKey);

    record StoredFile(String fileName, Long fileSize, String fileUrl, String storedKey) {
    }
}
