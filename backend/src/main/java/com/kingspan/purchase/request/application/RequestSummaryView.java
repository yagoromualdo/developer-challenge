package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.infra.PurchaseRequestEntity;
import com.kingspan.purchase.request.infra.RequestVersionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Linha da listagem: dados da versão atual, sem itens, etapas nem {@code allowedActions} (ficam no detalhe). */
public record RequestSummaryView(
        Long id,
        Long ownerId,
        Long costCenterId,
        RequestStatus status,
        int versionNumber,
        String title,
        Category category,
        BigDecimal total,
        LocalDate neededBy,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt) {

    static RequestSummaryView from(PurchaseRequestEntity request, RequestVersionEntity version) {
        return new RequestSummaryView(
                request.getId(),
                request.getOwnerId(),
                request.getCostCenterId(),
                request.getStatus(),
                version.getVersionNumber(),
                version.getTitle(),
                version.getCategory(),
                version.getTotal(),
                version.getNeededBy(),
                version.getSubmittedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
