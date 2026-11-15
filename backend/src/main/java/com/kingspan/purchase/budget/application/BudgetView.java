package com.kingspan.purchase.budget.application;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Orçamento de um mês; mês sem linha em {@code budget} aparece com tudo zerado (saldo zero, README 2.3). */
public record BudgetView(Long costCenterId, YearMonth month, BigDecimal amount, BigDecimal consumed) {

    public BigDecimal available() {
        return amount.subtract(consumed);
    }
}
