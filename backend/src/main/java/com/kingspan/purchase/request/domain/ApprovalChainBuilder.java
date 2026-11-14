package com.kingspan.purchase.request.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Monta a cadeia de aprovação de uma versão no momento da submissão, combinando as {@link ChainRule}s.
 *
 * <p>Os níveis exigidos são unidos num {@link EnumSet}: isso garante, por construção, que nenhuma etapa aparece
 * duplicada (ex.: {@code SOFTWARE} acima de 10.000,00 tem uma única etapa {@code ADMIN}) e que a ordem segue a
 * declaração de {@link StepLevel} ({@code NIVEL_1} → {@code NIVEL_2} → {@code ADMIN}), independente da ordem das
 * regras.
 */
public final class ApprovalChainBuilder {

    private final List<ChainRule> rules;

    public ApprovalChainBuilder(List<ChainRule> rules) {
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("É preciso ao menos uma regra de cadeia");
        }
        this.rules = List.copyOf(rules);
    }

    /** Cadeia padrão do README: faixas de valor + {@code SOFTWARE} exige {@code ADMIN}. */
    public static ApprovalChainBuilder standard() {
        return new ApprovalChainBuilder(List.of(new ThresholdRule(), new SoftwareRequiresAdminRule()));
    }

    /**
     * Etapas da cadeia, em ordem de aprovação, para o total e a categoria da versão.
     *
     * @return lista imutável, não vazia e sem níveis repetidos
     */
    public List<StepLevel> build(Money total, Category category) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(category, "category");
        Set<StepLevel> levels = EnumSet.noneOf(StepLevel.class);
        for (ChainRule rule : rules) {
            levels.addAll(rule.requiredLevels(total, category));
        }
        if (levels.isEmpty()) {
            // Com as regras padrão é impossível (ThresholdRule sempre exige NIVEL_1); protege configurações novas.
            throw new IllegalStateException(
                    "Nenhuma etapa de aprovação para total " + total + " e categoria " + category);
        }
        return List.copyOf(levels);
    }
}
