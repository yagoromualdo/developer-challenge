package com.kingspan.purchase.request.domain;

import java.util.Set;

/**
 * Regra plugável que contribui com etapas para a cadeia de aprovação (README, seção 2.3).
 *
 * <p>Cada regra só diz <em>quais</em> níveis exige; a ordem e a remoção de duplicatas ficam a cargo do {@link
 * ApprovalChainBuilder}. Assim uma regra nova (ex.: categoria que exige {@code NIVEL_2}) entra sem mexer nas demais.
 */
public interface ChainRule {

    /** Níveis exigidos por esta regra para a versão com o total e a categoria informados; vazio se não se aplica. */
    Set<StepLevel> requiredLevels(Money total, Category category);
}
