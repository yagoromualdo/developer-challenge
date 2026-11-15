package com.kingspan.purchase.request.domain;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.Optional;

public final class NoOtherStepApprovedRule implements ApprovalRule {

    @Override
    public Optional<RuleViolation> check(Actor actor, RequestSnapshot request, ApprovalStep currentStep) {
        return request.stepApprovedBy(actor.userId())
                .map(step -> new RuleViolation(
                        ErrorCode.DUPLICATE_APPROVER,
                        "você já aprovou a etapa " + step.level() + " desta versão (segregação de funções)."));
    }
}
