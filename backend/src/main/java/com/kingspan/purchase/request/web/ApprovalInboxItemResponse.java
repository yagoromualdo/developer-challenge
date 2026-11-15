package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.ApprovalInboxItemView;
import com.kingspan.purchase.request.application.RequestSummaryView;
import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import java.time.Instant;
import java.time.LocalDate;

/** Campos da listagem mais a etapa atual e o {@code lockVersion} exigido pelas ações; {@code total} como string. */
record ApprovalInboxItemResponse(
        Long id,
        Long ownerId,
        Long costCenterId,
        RequestStatus status,
        int version,
        long lockVersion,
        String title,
        Category category,
        String total,
        LocalDate neededBy,
        StepLevel currentStepLevel,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt) {

    static ApprovalInboxItemResponse from(ApprovalInboxItemView item) {
        RequestSummaryView summary = item.summary();
        return new ApprovalInboxItemResponse(
                summary.id(),
                summary.ownerId(),
                summary.costCenterId(),
                summary.status(),
                summary.versionNumber(),
                item.lockVersion(),
                summary.title(),
                summary.category(),
                summary.total().setScale(2).toPlainString(),
                summary.neededBy(),
                item.currentStepLevel(),
                summary.submittedAt(),
                summary.createdAt(),
                summary.updatedAt());
    }
}
