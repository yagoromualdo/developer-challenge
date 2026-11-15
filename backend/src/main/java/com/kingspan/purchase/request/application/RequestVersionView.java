package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.infra.RequestVersionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Uma versão da solicitação com os itens e a cadeia dela. Sem {@code submittedAt}, é a versão de trabalho ainda em
 * edição; com ele, está congelada (ADR 002).
 */
public record RequestVersionView(
        Long requestId,
        int versionNumber,
        boolean current,
        String title,
        String description,
        Category category,
        LocalDate neededBy,
        BigDecimal total,
        Instant submittedAt,
        YearMonth competenceMonth,
        Instant createdAt,
        List<RequestDetailView.Item> items,
        List<RequestDetailView.Step> steps) {

    static RequestVersionView from(RequestVersionEntity version) {
        return new RequestVersionView(
                version.getRequest().getId(),
                version.getVersionNumber(),
                isCurrent(version),
                version.getTitle(),
                version.getDescription(),
                version.getCategory(),
                version.getNeededBy(),
                version.getTotal(),
                version.getSubmittedAt(),
                version.getCompetenceMonth(),
                version.getCreatedAt(),
                version.getItems().stream().map(RequestDetailView.Item::from).toList(),
                version.getSteps().stream().map(RequestDetailView.Step::from).toList());
    }

    static boolean isCurrent(RequestVersionEntity version) {
        return version.getVersionNumber() == version.getRequest().getCurrentVersionNumber();
    }
}
