package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.RequestVersionView;
import com.kingspan.purchase.request.domain.Category;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Resposta de {@code GET /requests/:id/versions/:n}: conteúdo e cadeia da versão, sem {@code allowedActions}. */
record RequestVersionResponse(
        Long requestId,
        int version,
        boolean current,
        String title,
        String description,
        Category category,
        LocalDate neededBy,
        String total,
        Instant submittedAt,
        String competenceMonth,
        Instant createdAt,
        List<PurchaseRequestResponse.Item> items,
        List<PurchaseRequestResponse.Step> steps) {

    static RequestVersionResponse from(RequestVersionView view) {
        return new RequestVersionResponse(
                view.requestId(),
                view.versionNumber(),
                view.current(),
                view.title(),
                view.description(),
                view.category(),
                view.neededBy(),
                money(view.total()),
                view.submittedAt(),
                view.competenceMonth() == null ? null : view.competenceMonth().toString(),
                view.createdAt(),
                view.items().stream()
                        .map(item -> new PurchaseRequestResponse.Item(
                                item.description(), item.quantity(), money(item.unitPrice()), money(item.subtotal())))
                        .toList(),
                view.steps().stream()
                        .map(step -> new PurchaseRequestResponse.Step(
                                step.order(),
                                step.level(),
                                step.status(),
                                step.decidedBy(),
                                step.decidedAt(),
                                step.comment()))
                        .toList());
    }

    private static String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
