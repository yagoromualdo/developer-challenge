package com.kingspan.purchase.budget.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Orçamento mensal de um centro de custo. O {@code consumed} só muda pelo UPDATE condicional de {@link
 * BudgetRepository#consume}; a entidade não tem método para alterá-lo.
 */
@Entity
@Table(name = "budget")
public class BudgetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cost_center_id", nullable = false, updatable = false)
    private Long costCenterId;

    // 1º dia do mês de competência (ck_budget_month_first_day).
    @Column(nullable = false, updatable = false)
    private LocalDate month;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal consumed;

    protected BudgetEntity() {}

    public BudgetEntity(Long costCenterId, YearMonth month, BigDecimal amount, BigDecimal consumed) {
        this.costCenterId = Objects.requireNonNull(costCenterId, "costCenterId");
        this.month = Objects.requireNonNull(month, "month").atDay(1);
        this.amount = Objects.requireNonNull(amount, "amount");
        this.consumed = Objects.requireNonNull(consumed, "consumed");
    }

    public Long getId() {
        return id;
    }

    public Long getCostCenterId() {
        return costCenterId;
    }

    public YearMonth getMonth() {
        return YearMonth.from(month);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getConsumed() {
        return consumed;
    }
}
