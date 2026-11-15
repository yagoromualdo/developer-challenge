package com.kingspan.purchase.budget.application;

import com.kingspan.purchase.budget.infra.BudgetEntity;
import com.kingspan.purchase.budget.infra.BudgetRepository;
import com.kingspan.purchase.costcenter.application.CostCenterService;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.kingspan.purchase.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

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
