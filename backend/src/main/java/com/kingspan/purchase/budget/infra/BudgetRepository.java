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

    /**
     * Define o valor orçado do mês num único comando (ADR 001): cria a linha se o mês não tem orçamento ou atualiza
     * só se {@code consumed <= :amount}. Devolve 1 se gravou e 0 se o novo valor fica abaixo do já consumido.
     */
    default int defineAmount(Long costCenterId, YearMonth month, BigDecimal amount) {
        Objects.requireNonNull(costCenterId, "costCenterId");
        Objects.requireNonNull(month, "month");
        if (Objects.requireNonNull(amount, "amount").signum() < 0) {
            throw new IllegalArgumentException("Orçamento não pode ser negativo: " + amount.toPlainString());
        }
        return upsertAmountIfNotBelowConsumed(costCenterId, month.atDay(1), amount);
    }

    /**
     * Use {@link #defineAmount}. No conflito, o Postgres trava a linha e avalia o WHERE do DO UPDATE sobre a versão
     * mais recente: um consumo concorrente commitado antes entra na conta, e um posterior vê o novo amount. Dois PUTs
     * simultâneos num mês sem orçamento não violam a UNIQUE, porque o ON CONFLICT resolve a inserção especulativa.
     */
    // clearAutomatically: uma BudgetEntity já carregada no contexto ficaria com o amount antigo.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO budget (cost_center_id, month, amount, consumed)
            VALUES (:costCenterId, :month, :amount, 0)
            ON CONFLICT (cost_center_id, month)
            DO UPDATE SET amount = EXCLUDED.amount
             WHERE budget.consumed <= EXCLUDED.amount
            """)
    int upsertAmountIfNotBelowConsumed(Long costCenterId, LocalDate month, BigDecimal amount);
}
