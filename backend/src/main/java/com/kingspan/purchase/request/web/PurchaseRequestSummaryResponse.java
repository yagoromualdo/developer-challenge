package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.RequestSummaryView;
import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestStatus;
import java.time.Instant;
import java.time.LocalDate;

/** Item de {@code data[]} na listagem; {@code total} como string, igual ao detalhe. */
record PurchaseRequestSummaryResponse(
        Long id,
        Long ownerId,
        Long costCenterId,
        RequestStatus status,
        int version,
        String title,
        Category category,
        String total,
        LocalDate neededBy,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt) {

    static PurchaseRequestSummaryResponse from(RequestSummaryView view) {
        return new PurchaseRequestSummaryResponse(
                view.id(),
                view.ownerId(),
                view.costCenterId(),
                view.status(),
                view.versionNumber(),
                view.title(),
                view.category(),
                view.total().setScale(2).toPlainString(),
                view.neededBy(),
                view.submittedAt(),
                view.createdAt(),
                view.updatedAt());
    }
}
