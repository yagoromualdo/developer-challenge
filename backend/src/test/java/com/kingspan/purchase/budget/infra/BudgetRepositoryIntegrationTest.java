package com.kingspan.purchase.budget.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.support.DatabaseCleaner;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BudgetRepositoryIntegrationTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Autowired
    private BudgetRepository budgets;

    @Autowired
    private CostCenterRepository costCenters;

    @Autowired
    private JdbcTemplate jdbc;

    // Sem @Transactional no teste (backend/CLAUDE.md, seção 6): a transação é aberta só em volta do UPDATE, como no
    // service, e o commit acontece de verdade antes das asserções.
    @Autowired
    private TransactionTemplate transaction;

    private Long costCenterId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveConsumirQuandoOSaldoCabe() {
        saveBudget(costCenterId, NOVEMBER, "1000.00", "400.00");

        int rows = consume(costCenterId, NOVEMBER, "250.50");

        assertThat(rows).isEqualTo(1);
        assertThat(consumed(costCenterId, NOVEMBER)).isEqualByComparingTo("650.50");
    }

    @Test
    void deveConsumirQuandoOValorEsgotaExatamenteOSaldo() {
        saveBudget(costCenterId, NOVEMBER, "1000.00", "400.00");

        int rows = consume(costCenterId, NOVEMBER, "600.00");

        assertThat(rows).isEqualTo(1);
        assertThat(consumed(costCenterId, NOVEMBER)).isEqualByComparingTo("1000.00");
    }

    @Test
    void deveAfetarZeroLinhasQuandoOSaldoNaoCabe() {
        saveBudget(costCenterId, NOVEMBER, "1000.00", "400.00");

        int rows = consume(costCenterId, NOVEMBER, "600.01");

        assertThat(rows).isZero();
        assertThat(consumed(costCenterId, NOVEMBER)).isEqualByComparingTo("400.00");
    }

    @Test
    void deveAfetarZeroLinhasQuandoOMesNaoTemOrcamento() {
        saveBudget(costCenterId, NOVEMBER, "1000.00", "0.00");

        int rows = consume(costCenterId, DECEMBER, "0.01");

        assertThat(rows).isZero();
        assertThat(budgets.findByCostCenterIdAndMonth(costCenterId, DECEMBER)).isEmpty();
        assertThat(consumed(costCenterId, NOVEMBER)).isEqualByComparingTo("0.00");
    }

    @Test
    void deveConsumirSomenteOOrcamentoDoCentroDeCustoEDoMesInformados() {
        Long otherCostCenterId = costCenters
                .save(new CostCenterEntity("CC-RH-01", "Recursos Humanos", NOW))
                .getId();
        saveBudget(costCenterId, NOVEMBER, "1000.00", "0.00");
        saveBudget(costCenterId, DECEMBER, "1000.00", "0.00");
        saveBudget(otherCostCenterId, NOVEMBER, "1000.00", "0.00");

        int rows = consume(costCenterId, NOVEMBER, "100.00");

        assertThat(rows).isEqualTo(1);
        assertThat(consumed(costCenterId, NOVEMBER)).isEqualByComparingTo("100.00");
        assertThat(consumed(costCenterId, DECEMBER)).isEqualByComparingTo("0.00");
        assertThat(consumed(otherCostCenterId, NOVEMBER)).isEqualByComparingTo("0.00");
    }

    @Test
    void deveRecusarValorNaoPositivoSemTocarNoOrcamento() {
        saveBudget(costCenterId, NOVEMBER, "1000.00", "400.00");

        // O proxy do repositório traduz a exceção do método default para a hierarquia DataAccessException.
        assertThatThrownBy(() -> consume(costCenterId, NOVEMBER, "-100.00"))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
        assertThat(consumed(costCenterId, NOVEMBER)).isEqualByComparingTo("400.00");
    }

    private void saveBudget(Long costCenterId, YearMonth month, String amount, String consumed) {
        budgets.save(new BudgetEntity(costCenterId, month, new BigDecimal(amount), new BigDecimal(consumed)));
    }

    private int consume(Long costCenterId, YearMonth month, String value) {
        return transaction.execute(status -> budgets.consume(costCenterId, month, new BigDecimal(value)));
    }

    private BigDecimal consumed(Long costCenterId, YearMonth month) {
        return budgets.findByCostCenterIdAndMonth(costCenterId, month)
                .orElseThrow()
                .getConsumed();
    }
}
