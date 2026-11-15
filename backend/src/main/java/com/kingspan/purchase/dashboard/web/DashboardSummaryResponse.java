package com.kingspan.purchase.dashboard.web;

import com.kingspan.purchase.budget.application.BudgetView;
import com.kingspan.purchase.costcenter.application.CostCenterView;
import com.kingspan.purchase.dashboard.application.CostCenterBudgetView;
import com.kingspan.purchase.dashboard.application.DashboardSummaryView;
import com.kingspan.purchase.request.domain.RequestStatus;
import java.util.List;
import java.util.Map;

/** Mês como {@code yyyy-MM} e valores monetários como string ({@code "4899.90"}). */
record DashboardSummaryResponse(
        String month, Map<RequestStatus, Long> requestsByStatus, long totalRequests, List<BudgetSummary> budgets) {

    static DashboardSummaryResponse from(DashboardSummaryView view) {
        return new DashboardSummaryResponse(
                view.month().toString(),
                view.requestsByStatus(),
                view.totalRequests(),
                view.budgets().stream().map(BudgetSummary::from).toList());
    }

    record BudgetSummary(
            Long costCenterId,
            String costCenterCode,
            String costCenterName,
            String amount,
            String consumed,
            String available) {

        static BudgetSummary from(CostCenterBudgetView view) {
            CostCenterView costCenter = view.costCenter();
            BudgetView budget = view.budget();
            return new BudgetSummary(
                    costCenter.id(),
                    costCenter.code(),
                    costCenter.name(),
                    budget.amount().toPlainString(),
                    budget.consumed().toPlainString(),
                    budget.available().toPlainString());
        }
    }
}
