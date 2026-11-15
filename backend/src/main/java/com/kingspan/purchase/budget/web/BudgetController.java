package com.kingspan.purchase.budget.web;

import com.kingspan.purchase.budget.application.BudgetService;
import jakarta.validation.Valid;
import java.time.YearMonth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code month} no formato {@code yyyy-MM}; o PUT é exclusivo de ADMIN (SecurityFilterChain). */
@RestController
@RequestMapping("/cost-centers/{costCenterId}/budgets/{month}")
class BudgetController {

    private final BudgetService budgetService;

    BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    BudgetResponse get(@PathVariable Long costCenterId, @PathVariable YearMonth month) {
        return BudgetResponse.from(budgetService.get(costCenterId, month));
    }

    @PutMapping
    BudgetResponse define(
            @PathVariable Long costCenterId,
            @PathVariable YearMonth month,
            @Valid @RequestBody DefineBudgetRequest request) {
        return BudgetResponse.from(budgetService.define(costCenterId, month, request.amount()));
    }
}
