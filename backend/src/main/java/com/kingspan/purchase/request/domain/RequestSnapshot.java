package com.kingspan.purchase.request.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@code steps} traz só a versão atual, em ordem; por isso aprovações da v1 não contam na v2. */
public record RequestSnapshot(Long ownerId, Long costCenterId, RequestStatus status, List<ApprovalStep> steps) {

    public RequestSnapshot {
        Objects.requireNonNull(ownerId, "ownerId é obrigatório");
        Objects.requireNonNull(costCenterId, "costCenterId é obrigatório");
        Objects.requireNonNull(status, "status é obrigatório");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps é obrigatório"));
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }

    public Optional<ApprovalStep> currentStep() {
        return steps.stream().filter(ApprovalStep::isPending).findFirst();
    }

    public Optional<ApprovalStep> stepApprovedBy(Long userId) {
        return steps.stream().filter(step -> step.wasApprovedBy(userId)).findFirst();
    }
}
