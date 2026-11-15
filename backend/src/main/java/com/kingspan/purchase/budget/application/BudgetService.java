package com.kingspan.purchase.budget.application;

import com.kingspan.purchase.budget.infra.BudgetEntity;
import com.kingspan.purchase.budget.infra.BudgetRepository;
import com.kingspan.purchase.costcenter.application.CostCenterService;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.kingspan.purchase.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final BudgetRepository budgets;
    private final CostCenterService costCenters;

    public BudgetService(BudgetRepository budgets, CostCenterService costCenters) {
        this.budgets = budgets;
        this.costCenters = costCenters;
    }

    @Transactional(readOnly = true)
    public BudgetView get(Long costCenterId, YearMonth month) {
        requireExistingCostCenter(costCenterId);
        return find(costCenterId, month);
    }

    /** Orçamentos de um mês numa só consulta, na ordem de {@code costCenterIds}; mês sem orçamento sai zerado. */
    @Transactional(readOnly = true)
    public List<BudgetView> list(List<Long> costCenterIds, YearMonth month) {
        if (costCenterIds.isEmpty()) {
            return List.of();
        }
        Map<Long, BudgetView> defined = budgets.findByMonthAndCostCenterIdIn(month, costCenterIds).stream()
                .map(BudgetService::toView)
                .collect(Collectors.toMap(BudgetView::costCenterId, Function.identity()));
        return costCenterIds.stream()
                .map(id -> defined.getOrDefault(id, new BudgetView(id, month, ZERO, ZERO)))
                .toList();
    }

    /**
     * Sem ler antes de gravar: a checagem {@code consumed <= amount} acontece dentro do próprio UPDATE, então uma
     * aprovação concorrente nunca deixa o orçamento abaixo do consumido (nem cai no CHECK como erro 500).
     */
    @Transactional
    public BudgetView define(Long costCenterId, YearMonth month, BigDecimal amount) {
        requireExistingCostCenter(costCenterId);
        // A escala já foi validada na borda (no máximo 2 casas); aqui só normaliza "1500" para "1500.00".
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (budgets.defineAmount(costCenterId, month, normalized) == 0) {
            throw belowConsumed(month, normalized, find(costCenterId, month).consumed());
        }
        // Lido na mesma transação, depois do UPDATE: a linha está travada até o commit, então o consumed é exato.
        BudgetView defined = find(costCenterId, month);
        log.info(
                "Orçamento definido: centro de custo {}, mês {}, orçado {}, consumido {}",
                costCenterId,
                month,
                defined.amount(),
                defined.consumed());
        return defined;
    }

    /**
     * Consome {@code value} do mês de competência pelo UPDATE condicional (ADR 001); sem saldo, lança o 422
     * {@code BUDGET_EXCEEDED} e a transação do chamador é desfeita.
     */
    @Transactional
    public void consume(Long costCenterId, YearMonth month, BigDecimal value) {
        if (budgets.consume(costCenterId, month, value) == 0) {
            throw budgetExceeded(costCenterId, month, value);
        }
        log.info("Orçamento consumido: centro de custo {}, mês {}, valor {}", costCenterId, month, value);
    }

    // O saldo lido aqui é só informativo: quem decidiu que não cabia foi o UPDATE condicional.
    private BusinessRuleException budgetExceeded(Long costCenterId, YearMonth month, BigDecimal required) {
        BigDecimal available = find(costCenterId, month).available();
        return new BusinessRuleException(
                ErrorCode.BUDGET_EXCEEDED,
                "O centro de custo " + costCenters.codeOf(costCenterId) + " tem " + brl(available) + " disponíveis em "
                        + month + "; a solicitação exige " + brl(required) + ".");
    }

    private static String brl(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + format.format(value);
    }

    private BudgetView find(Long costCenterId, YearMonth month) {
        return budgets.findByCostCenterIdAndMonth(costCenterId, month)
                .map(BudgetService::toView)
                .orElseGet(() -> new BudgetView(costCenterId, month, ZERO, ZERO));
    }

    private void requireExistingCostCenter(Long costCenterId) {
        if (!costCenters.exists(costCenterId)) {
            throw new ResourceNotFoundException("Centro de custo " + costCenterId + " não encontrado.");
        }
    }

    private static BudgetView toView(BudgetEntity entity) {
        return new BudgetView(entity.getCostCenterId(), entity.getMonth(), entity.getAmount(), entity.getConsumed());
    }

    private static BusinessRuleException belowConsumed(YearMonth month, BigDecimal requested, BigDecimal consumed) {
        return new BusinessRuleException(
                ErrorCode.BUDGET_BELOW_CONSUMED,
                "Não é possível definir o orçamento de " + month + " em " + requested.toPlainString()
                        + ": já foram consumidos " + consumed.toPlainString()
                        + " neste mês, e o orçamento não pode ficar abaixo do consumido.");
    }
}
