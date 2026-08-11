package com.example.likelionhackathon.domain.handover.dto;

import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ItemCategory;
import com.example.likelionhackathon.domain.handover.entity.HandoverEnums.ReviewStatus;

import java.util.List;

public record OpenAiHandoverResult(List<GeneratedItem> items) {

    public record GeneratedItem(
            ItemCategory category,
            String title,
            String description,
            Long assigneeMemberId,
            ReviewStatus reviewStatus,
            List<Integer> evidenceIndexes
    ) {
    }
}
