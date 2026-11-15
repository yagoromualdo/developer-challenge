package com.kingspan.purchase.budget.web;

import com.kingspan.purchase.budget.application.BudgetView;

/** Valores monetários como string ({@code "4899.90"}) e mês como {@code yyyy-MM}. */
record BudgetResponse(Long costCenterId, String month, String amount, String consumed, String available) {

    static BudgetResponse from(BudgetView view) {
        return new BudgetResponse(
                view.costCenterId(),
                view.month().toString(),
                view.amount().toPlainString(),
                view.consumed().toPlainString(),
                view.available().toPlainString());
    }
}
