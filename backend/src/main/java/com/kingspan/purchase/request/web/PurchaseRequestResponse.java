package com.kingspan.purchase.request.web;

import com.kingspan.purchase.request.application.RequestDetailView;
import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestAction;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.domain.StepStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Detalhe da versão atual. Valores monetários como string ({@code "4899.90"}); {@code version} é a versão de negócio
 * (v1, v2...) e {@code lockVersion} é o controle de concorrência, que muda a cada alteração.
 */
record PurchaseRequestResponse(
        Long id,
        Long ownerId,
        Long costCenterId,
        RequestStatus status,
        int version,
        long lockVersion,
        String title,
        String description,
        Category category,
        LocalDate neededBy,
        String total,
        Instant submittedAt,
        String competenceMonth,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items,
        List<Step> steps,
        List<RequestAction> allowedActions) {

    record Item(String description, int quantity, String unitPrice, String subtotal) {}

    record Step(int order, StepLevel level, StepStatus status, Long decidedBy, Instant decidedAt, String comment) {}

    static PurchaseRequestResponse from(RequestDetailView view) {
        return new PurchaseRequestResponse(
                view.id(),
                view.ownerId(),
                view.costCenterId(),
                view.status(),
                view.versionNumber(),
                view.lockVersion(),
                view.title(),
                view.description(),
                view.category(),
                view.neededBy(),
                money(view.total()),
                view.submittedAt(),
                view.competenceMonth() == null ? null : view.competenceMonth().toString(),
                view.createdAt(),
                view.updatedAt(),
                view.items().stream()
                        .map(item -> new Item(
                                item.description(), item.quantity(), money(item.unitPrice()), money(item.subtotal())))
                        .toList(),
                view.steps().stream()
                        .map(step -> new Step(
                                step.order(),
                                step.level(),
                                step.status(),
                                step.decidedBy(),
                                step.decidedAt(),
                                step.comment()))
                        .toList(),
                view.allowedActions());
    }

    private static String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
