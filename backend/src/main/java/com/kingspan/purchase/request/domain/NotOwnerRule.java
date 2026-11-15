package com.kingspan.purchase.request.domain;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.Optional;

/** Segregação de funções: ninguém decide o próprio pedido, nem um {@code ADMIN}. */
public final class NotOwnerRule implements ApprovalRule {

    @Override
    public Optional<RuleViolation> check(Actor actor, RequestSnapshot request, ApprovalStep currentStep) {
        if (!request.isOwnedBy(actor.userId())) {
            return Optional.empty();
        }
        return Optional.of(new RuleViolation(
                ErrorCode.SELF_APPROVAL_FORBIDDEN,
                "você é o dono desta solicitação, e ninguém pode decidir o próprio pedido (segregação de funções)."));
    }
}
