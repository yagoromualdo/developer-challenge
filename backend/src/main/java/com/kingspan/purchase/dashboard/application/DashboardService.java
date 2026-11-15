package com.kingspan.purchase.dashboard.application;

import com.kingspan.purchase.budget.application.BudgetService;
import com.kingspan.purchase.budget.application.BudgetView;
import com.kingspan.purchase.costcenter.application.CostCenterService;
import com.kingspan.purchase.costcenter.application.CostCenterView;
import com.kingspan.purchase.request.application.PurchaseRequestService;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.BusinessTime;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard (README 2.6): só compõe as portas de entrada das outras features, sem ler a infra delas. Os totais usam a
 * visibilidade da listagem; o orçamento, o centro de custo do ator (todos para o ADMIN).
 */
@Service
public class DashboardService {

    private final PurchaseRequestService requests;
    private final BudgetService budgets;
    private final CostCenterService costCenters;
    private final Clock clock;

    public DashboardService(
            PurchaseRequestService requests, BudgetService budgets, CostCenterService costCenters, Clock clock) {
        this.requests = requests;
        this.budgets = budgets;
        this.costCenters = costCenters;
        this.clock = clock;
    }

    /** O mês corrente é o de competência de uma submissão feita agora, ou seja, o orçamento que ela consumiria. */
    @Transactional(readOnly = true)
    public DashboardSummaryView summary(Actor actor) {
        YearMonth month = BusinessTime.competenceOf(clock.instant());
        return new DashboardSummaryView(
                month, requests.countByStatus(actor), budgetsOf(costCentersInScope(actor), month));
    }

    private List<CostCenterView> costCentersInScope(Actor actor) {
        if (actor.isAdmin()) {
            return costCenters.list();
        }
        // O banco exige centro de custo para quem não é ADMIN (ck_app_user_cost_center_required).
        return costCenters.list().stream()
                .filter(costCenter -> costCenter.id().equals(actor.costCenterId()))
                .toList();
    }

    private List<CostCenterBudgetView> budgetsOf(List<CostCenterView> scope, YearMonth month) {
        Map<Long, BudgetView> byCostCenter =
                budgets.list(scope.stream().map(CostCenterView::id).toList(), month).stream()
                        .collect(Collectors.toMap(BudgetView::costCenterId, Function.identity()));
        return scope.stream()
                .map(costCenter -> new CostCenterBudgetView(costCenter, byCostCenter.get(costCenter.id())))
                .toList();
    }
}
