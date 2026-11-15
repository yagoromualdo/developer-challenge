package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.ApprovalStep;
import com.kingspan.purchase.request.domain.RequestSnapshot;
import com.kingspan.purchase.request.domain.StepLevel;
import com.kingspan.purchase.request.infra.RequestVersionEntity;

/**
 * Linha da caixa de aprovação: o resumo da listagem mais a etapa que o ator decidiria e o {@code lockVersion}, para o
 * front aprovar direto da caixa sem abrir o detalhe.
 */
public record ApprovalInboxItemView(RequestSummaryView summary, StepLevel currentStepLevel, long lockVersion) {

    static ApprovalInboxItemView from(RequestVersionEntity currentVersion, RequestSnapshot snapshot) {
        return new ApprovalInboxItemView(
                RequestSummaryView.from(currentVersion.getRequest(), currentVersion),
                snapshot.currentStep()
                        .map(ApprovalStep::level)
                        .orElseThrow(() -> new IllegalStateException("Item da caixa sem etapa pendente")),
                currentVersion.getRequest().getLockVersion());
    }
}
