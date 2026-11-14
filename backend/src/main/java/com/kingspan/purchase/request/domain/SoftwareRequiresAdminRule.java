package com.kingspan.purchase.request.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Compras de {@link Category#SOFTWARE} sempre exigem a etapa {@code ADMIN} (licenciamento e segurança), mesmo abaixo
 * de 10.000,00 (README, seção 2.3, regra 1). Acima desse valor a {@link ThresholdRule} também exige {@code ADMIN}; a
 * duplicata é eliminada pelo {@link ApprovalChainBuilder}.
 */
public final class SoftwareRequiresAdminRule implements ChainRule {

    @Override
    public Set<StepLevel> requiredLevels(Money total, Category category) {
        Objects.requireNonNull(category, "category");
        return category == Category.SOFTWARE ? EnumSet.of(StepLevel.ADMIN) : EnumSet.noneOf(StepLevel.class);
    }
}
