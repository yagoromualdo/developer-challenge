package com.kingspan.purchase.dashboard.application;

import com.kingspan.purchase.request.domain.RequestStatus;
import java.time.YearMonth;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** {@code requestsByStatus} traz todos os status, na ordem do enum, com zero quando não há nenhuma solicitação. */
public record DashboardSummaryView(
        YearMonth month, Map<RequestStatus, Long> requestsByStatus, List<CostCenterBudgetView> budgets) {

    public DashboardSummaryView {
        requestsByStatus = Collections.unmodifiableMap(new EnumMap<>(requestsByStatus));
        budgets = List.copyOf(budgets);
    }

    public long totalRequests() {
        return requestsByStatus.values().stream().mapToLong(Long::longValue).sum();
    }
}
