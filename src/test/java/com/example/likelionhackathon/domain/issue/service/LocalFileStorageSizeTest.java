package com.example.likelionhackathon.domain.issue.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 첨부 크기 조회는 "파일이 없다" 와 "확인하지 못했다" 를 구분해야 한다.
 * 둘 다 크기 없음으로 처리하면 크기를 알 수 있는 파일이 크기 없이 저장된다.
 */
class LocalFileStorageSizeTest {

    @TempDir
    Path uploadDirectory;

    @Test
    void readsTheSizeOfAStoredFile() throws IOException {
        Files.writeString(uploadDirectory.resolve("abc_보고서.pdf"), "12345");

        assertThat(storage().sizeOf("abc_보고서.pdf")).isEqualTo(5);
    }

    @Test
    void returnsNothingWhenTheFileIsAbsent() {
        assertThat(storage().sizeOf("없는파일.pdf")).isNull();
    }

    @Test
    void returnsNothingForADirectory() throws IOException {
        // 읽을 수 있는 디렉터리는 isReadable 을 통과한다. 크기를 물어볼 대상이 아니다.
        Files.createDirectory(uploadDirectory.resolve("폴더"));

        assertThat(storage().sizeOf("폴더")).isNull();
    }

    @Test
    void refusesToLookOutsideTheUploadDirectory() throws IOException {
        Files.writeString(uploadDirectory.resolveSibling("바깥.pdf"), "12345");

        assertThat(storage().sizeOf("../바깥.pdf")).isNull();
    }

    private LocalFileStorage storage() {
        return new LocalFileStorage(uploadDirectory.toString(), "http://localhost:8080");
    }
}
