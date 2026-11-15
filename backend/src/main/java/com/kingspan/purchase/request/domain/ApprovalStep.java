package com.kingspan.purchase.request.domain;

import java.util.Objects;

public record ApprovalStep(StepLevel level, StepStatus status, Long decidedBy) {

    public ApprovalStep {
        Objects.requireNonNull(level, "level é obrigatório");
        Objects.requireNonNull(status, "status é obrigatório");
        if (status == StepStatus.PENDING && decidedBy != null) {
            throw new IllegalArgumentException("Etapa pendente não pode ter quem decidiu");
        }
        if (status != StepStatus.PENDING && decidedBy == null) {
            throw new IllegalArgumentException("Etapa decidida precisa de quem decidiu");
        }
    }

    public static ApprovalStep pending(StepLevel level) {
        return new ApprovalStep(level, StepStatus.PENDING, null);
    }

    public static ApprovalStep approvedBy(StepLevel level, Long userId) {
        return new ApprovalStep(level, StepStatus.APPROVED, userId);
    }

    public boolean isPending() {
        return status == StepStatus.PENDING;
    }

    public boolean wasApprovedBy(Long userId) {
        return status == StepStatus.APPROVED && decidedBy.equals(userId);
    }
}
