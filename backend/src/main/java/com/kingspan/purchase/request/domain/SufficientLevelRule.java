package com.kingspan.purchase.request.domain;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.Optional;

public final class SufficientLevelRule implements ApprovalRule {

    @Override
    public Optional<RuleViolation> check(Actor actor, RequestSnapshot request, ApprovalStep currentStep) {
        if (canDecide(actor, currentStep.level())) {
            return Optional.empty();
        }
        return Optional.of(new RuleViolation(
                ErrorCode.APPROVER_NOT_ELIGIBLE,
                "a etapa atual é " + currentStep.level() + " e exige " + requirement(currentStep.level()) + "; você é "
                        + describe(actor) + "."));
    }

    private static boolean canDecide(Actor actor, StepLevel level) {
        if (actor.isAdmin()) {
            return true;
        }
        if (!actor.isApprover()) {
            return false;
        }
        return switch (level) {
            case NIVEL_1 -> true;
            case NIVEL_2 -> actor.approverLevel() == ApproverLevel.NIVEL_2;
            case ADMIN -> false;
        };
    }

    private static String requirement(StepLevel level) {
        return switch (level) {
            case NIVEL_1 -> "um APROVADOR (NIVEL_1 ou NIVEL_2) ou um ADMIN";
            case NIVEL_2 -> "um APROVADOR NIVEL_2 ou um ADMIN";
            case ADMIN -> "um ADMIN";
        };
    }

    private static String describe(Actor actor) {
        return actor.isApprover()
                ? "APROVADOR " + actor.approverLevel()
                : actor.role().name();
    }
}
