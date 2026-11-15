package com.kingspan.purchase.request.domain;

import com.kingspan.purchase.shared.domain.Actor;
import java.util.Optional;

/** Elegibilidade para aprovar, rejeitar ou devolver a etapa atual (README 2.3, regras 2 e 3). */
public interface ApprovalRule {

    Optional<RuleViolation> check(Actor actor, RequestSnapshot request, ApprovalStep currentStep);
}
