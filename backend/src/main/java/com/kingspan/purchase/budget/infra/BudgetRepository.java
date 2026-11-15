package com.kingspan.purchase.budget.infra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BudgetRepository extends JpaRepository<BudgetEntity, Long> {

    Optional<BudgetEntity> findByCostCenterIdAndMonth(Long costCenterId, LocalDate month);

    default Optional<BudgetEntity> findByCostCenterIdAndMonth(Long costCenterId, YearMonth month) {
        return findByCostCenterIdAndMonth(costCenterId, month.atDay(1));
    }

    /**
     * Consome {@code value} do orçamento do mês num único UPDATE condicional (ADR 001) e devolve as linhas afetadas:
     * 1 se coube, 0 se o saldo é insuficiente ou o mês não tem orçamento (saldo zero). Exige uma transação aberta.
     */
    default int consume(Long costCenterId, YearMonth month, BigDecimal value) {
        Objects.requireNonNull(costCenterId, "costCenterId");
        Objects.requireNonNull(month, "month");
        // Valor negativo "devolveria" saldo por um caminho que não é estorno.
        if (Objects.requireNonNull(value, "value").signum() <= 0) {
            throw new IllegalArgumentException("Valor a consumir precisa ser positivo: " + value.toPlainString());
        }
        return consumeIfAvailable(costCenterId, month.atDay(1), value);
    }

    /**
     * Use {@link #consume}. Sem SELECT FOR UPDATE: no READ COMMITTED, um UPDATE que espera o lock da linha reavalia o
     * WHERE com o {@code consumed} já commitado pelo concorrente, então duas aprovações nunca estouram o saldo.
     */
    // Sem clearAutomatically: limparia o contexto e descartaria as alterações da solicitação na mesma transação.
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE budget
               SET consumed = consumed + :value
             WHERE cost_center_id = :costCenterId
               AND month = :month
               AND amount - consumed >= :value
            """)
    int consumeIfAvailable(Long costCenterId, LocalDate month, BigDecimal value);
}
