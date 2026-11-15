package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.RequestAction;
import com.kingspan.purchase.request.domain.RequestStatus;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.domain.StepStatus;
import com.kingspan.purchase.request.infra.ApprovalStepEntity;
import com.kingspan.purchase.request.infra.PurchaseRequestEntity;
import com.kingspan.purchase.request.infra.RequestItemEntity;
import com.kingspan.purchase.request.infra.RequestVersionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** Versão atual da solicitação com itens, etapas e as ações que o ator pode executar agora (na ordem do enum). */
public record RequestDetailView(
        Long id,
        Long ownerId,
        Long costCenterId,
        RequestStatus status,
        int versionNumber,
        long lockVersion,
        String title,
        String description,
        Category category,
        LocalDate neededBy,
        BigDecimal total,
        Instant submittedAt,
        YearMonth competenceMonth,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items,
        List<Step> steps,
        List<RequestAction> allowedActions) {

    public record Item(String description, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {

        static Item from(RequestItemEntity item) {
            BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return new Item(item.getDescription(), item.getQuantity(), item.getUnitPrice(), subtotal);
        }
    }

    public record Step(
            int order, StepLevel level, StepStatus status, Long decidedBy, Instant decidedAt, String comment) {

        static Step from(ApprovalStepEntity step) {
            return new Step(
                    step.getStepOrder(),
                    step.getLevel(),
                    step.getStatus(),
                    step.getDecidedBy(),
                    step.getDecidedAt(),
                    step.getComment());
        }
    }

    static RequestDetailView from(PurchaseRequestEntity request, Set<RequestAction> allowedActions) {
        RequestVersionEntity version = request.getCurrentVersion();
        return new RequestDetailView(
                request.getId(),
                request.getOwnerId(),
                request.getCostCenterId(),
                request.getStatus(),
                version.getVersionNumber(),
                request.getLockVersion(),
                version.getTitle(),
                version.getDescription(),
                version.getCategory(),
                version.getNeededBy(),
                version.getTotal(),
                version.getSubmittedAt(),
                version.getCompetenceMonth(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                version.getItems().stream().map(Item::from).toList(),
                version.getSteps().stream().map(Step::from).toList(),
                allowedActions.stream().sorted().toList());
    }
}
