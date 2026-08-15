package com.example.likelionhackathon.domain.cycle.dto;

import com.example.likelionhackathon.domain.cycle.entity.CycleEnums.CycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class CycleRequest {

    private CycleRequest() {
    }

    public record Create(
            @NotBlank(message = "사이클 이름을 입력해주세요.") @Size(max = 100) String name,
            @NotNull(message = "시작 일자를 입력해주세요.") LocalDate startDate,
            @NotNull(message = "마감 일자를 입력해주세요.") LocalDate endDate,
            @Size(max = 1000) String goal
    ) {
    }

    public record Update(
            @NotBlank(message = "사이클 이름을 입력해주세요.") @Size(max = 100) String name,
            @NotNull(message = "시작 일자를 입력해주세요.") LocalDate startDate,
            @NotNull(message = "마감 일자를 입력해주세요.") LocalDate endDate,
            @Size(max = 1000) String goal
    ) {
    }

    public record ChangeStatus(
            @NotNull(message = "변경할 상태를 입력해주세요.") CycleStatus status,
            Boolean moveUnfinishedIssues,
            Long targetCycleId
    ) {
        public boolean shouldMoveUnfinishedIssues() {
            return Boolean.TRUE.equals(moveUnfinishedIssues);
        }
    }

    public record RunAnalysis(
            Boolean force
    ) {
        public boolean isForced() {
            return Boolean.TRUE.equals(force);
        }
    }
}
