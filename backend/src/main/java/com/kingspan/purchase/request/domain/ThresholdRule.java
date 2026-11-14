package com.kingspan.purchase.request.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Etapas por faixa de valor total (README, seção 2.3): até 1.000,00 → {@code NIVEL_1}; até 10.000,00 → + {@code
 * NIVEL_2}; acima de 10.000,00 → + {@code ADMIN}.
 *
 * <p>Os limites são inclusivos ("até"): exatamente 1.000,00 fica só com {@code NIVEL_1} e exatamente 10.000,00 não
 * exige {@code ADMIN}. A comparação usa {@link Money#isGreaterThan}, sem arredondamento.
 */
public final class ThresholdRule implements ChainRule {

    static final Money LEVEL_2_ABOVE = Money.of("1000.00");
    static final Money ADMIN_ABOVE = Money.of("10000.00");

    @Override
    public Set<StepLevel> requiredLevels(Money total, Category category) {
        Objects.requireNonNull(total, "total");
        Set<StepLevel> levels = EnumSet.of(StepLevel.NIVEL_1);
        if (total.isGreaterThan(LEVEL_2_ABOVE)) {
            levels.add(StepLevel.NIVEL_2);
        }
        if (total.isGreaterThan(ADMIN_ABOVE)) {
            levels.add(StepLevel.ADMIN);
        }
        return levels;
    }
}
