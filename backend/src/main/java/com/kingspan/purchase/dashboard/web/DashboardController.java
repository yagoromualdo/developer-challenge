package com.kingspan.purchase.dashboard.web;

import com.kingspan.purchase.dashboard.application.DashboardService;
import com.kingspan.purchase.shared.domain.Actor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aberto a qualquer papel: o escopo dos totais e do orçamento vem do ator, não de um 403. */
@RestController
@RequestMapping("/dashboard")
class DashboardController {

    private final DashboardService dashboardService;

    DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    DashboardSummaryResponse summary(Actor actor) {
        return DashboardSummaryResponse.from(dashboardService.summary(actor));
    }
}
