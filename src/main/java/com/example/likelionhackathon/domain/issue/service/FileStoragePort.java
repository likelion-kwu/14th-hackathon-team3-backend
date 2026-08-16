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

    /**
     * 저장된 파일의 크기. 내용은 읽지 않고 메타데이터만 본다.
     *
     * <p>이슈에 첨부를 붙일 때 클라이언트는 URL 만 보낸다.
     * 크기를 함께 받아 그대로 믿는 대신 저장소에 직접 물어본다.</p>
     *
     * <p>{@code null} 은 <b>파일이 없다고 확인된 경우에만</b> 돌려준다.
     * 권한 오류나 저장소 장애까지 {@code null} 로 뭉개면 크기를 알 수 있는 파일이
     * 크기 없이 첨부로 저장된다.</p>
     *
     * @return 크기(byte). 파일이 없으면 {@code null}
     * @throws com.example.likelionhackathon.global.error.exception.CustomException 저장소를 확인하지 못한 경우
     */
    Long sizeOf(String storedKey);

    record StoredFile(String fileName, Long fileSize, String fileUrl, String storedKey) {
    }
}
