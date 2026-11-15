package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.infra.RequestVersionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

/** Linha da lista de versões, sem itens nem etapas (que ficam em {@code /versions/:n}). */
public record RequestVersionSummaryView(
        int versionNumber,
        boolean current,
        String title,
        Category category,
        BigDecimal total,
        Instant submittedAt,
        YearMonth competenceMonth,
        Instant createdAt) {

    static RequestVersionSummaryView from(RequestVersionEntity version) {
        return new RequestVersionSummaryView(
                version.getVersionNumber(),
                RequestVersionView.isCurrent(version),
                version.getTitle(),
                version.getCategory(),
                version.getTotal(),
                version.getSubmittedAt(),
                version.getCompetenceMonth(),
                version.getCreatedAt());
    }
}
