package com.example.likelionhackathon.domain.issue.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일 저장소.
 *
 * <p>명세는 S3 업로드를 요구하지만 버킷 · 자격증명 · SDK 의존성은 팀 인프라 결정 사항이라
 * 인터페이스로 끊어 두었다. {@link LocalFileStorage} 가 기본 구현이고,
 * S3 를 붙일 때는 이 인터페이스만 구현하면 서비스 코드는 바뀌지 않는다.</p>
 */
public interface FileStoragePort {

    StoredFile store(MultipartFile file);

    /**
     * 저장된 파일을 읽어온다. 다운로드 엔드포인트가 사용한다.
     */
    Resource load(String storedName);

    record StoredFile(String fileName, Long fileSize, String fileUrl) {
    }
}
