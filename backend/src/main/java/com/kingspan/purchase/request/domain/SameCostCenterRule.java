package com.kingspan.purchase.request.domain;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.Optional;

public final class SameCostCenterRule implements ApprovalRule {

    @Override
    public Optional<RuleViolation> check(Actor actor, RequestSnapshot request, ApprovalStep currentStep) {
        if (actor.isAdmin() || request.costCenterId().equals(actor.costCenterId())) {
            return Optional.empty();
        }
        return Optional.of(new RuleViolation(
                ErrorCode.APPROVER_NOT_ELIGIBLE,
                "ela pertence ao centro de custo " + request.costCenterId()
                        + ", e você só pode decidir solicitações do seu centro de custo ("
                        + actor.costCenterId() + ")."));
    }
}
