package com.kingspan.purchase.dashboard.application;

import com.kingspan.purchase.budget.application.BudgetView;
import com.kingspan.purchase.costcenter.application.CostCenterView;

public record CostCenterBudgetView(CostCenterView costCenter, BudgetView budget) {}
