package com.kingspan.purchase.budget.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.budget.infra.BudgetEntity;
import com.kingspan.purchase.budget.infra.BudgetRepository;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.security.JwtService;
import com.kingspan.purchase.support.ApiClient;
import com.kingspan.purchase.support.ApiClient.Response;
import com.kingspan.purchase.support.DatabaseCleaner;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class BudgetIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CostCenterRepository costCenters;

    @Autowired
    private BudgetRepository budgets;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JsonMapper json;

    private ApiClient api;
    private Long costCenterId;
    private String adminToken;
    private String path;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        adminToken = token(new Actor(1L, Role.ADMIN, null, null));
        path = "/cost-centers/" + costCenterId + "/budgets/2026-11";
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveRetornarSaldoZeroParaMesSemOrcamento() {
        Response response = api.get(path, token(new Actor(2L, Role.SOLICITANTE, costCenterId, null)));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("costCenterId", costCenterId.intValue())
                .containsEntry("month", "2026-11")
                .containsEntry("amount", "0.00")
                .containsEntry("consumed", "0.00")
                .containsEntry("available", "0.00");
        assertThat(budgets.count()).isZero();
    }

    @Test
    void deveRetornarOrcamentoConsumidoEDisponivelComoString() {
        budgets.save(new BudgetEntity(costCenterId, MONTH, new BigDecimal("10000.00"), new BigDecimal("9850.10")));

        Response response = api.get(path, adminToken);

        assertThat(response.json())
                .containsEntry("amount", "10000.00")
                .containsEntry("consumed", "9850.10")
                .containsEntry("available", "149.90");
    }

    @Test
    void deveRetornar404ParaCentroDeCustoInexistente() {
        api.get("/cost-centers/999/budgets/2026-11", adminToken).assertProblem(404, "NOT_FOUND");
        api.put("/cost-centers/999/budgets/2026-11", adminToken, "{\"amount\":\"100.00\"}")
                .assertProblem(404, "NOT_FOUND");
    }

    @Test
    void deveRetornar400ParaMesEmFormatoInvalido() {
        for (String month : List.of("2026-13", "2026-1", "novembro")) {
            Response response = api.get("/cost-centers/" + costCenterId + "/budgets/" + month, adminToken);

            response.assertProblem(400, "VALIDATION_FAILED");
            assertThat(response.errorFields()).containsExactly("month");
        }
    }

    @Test
    void deveRetornar403QuandoNaoAdminDefineOrcamento() {
        String approver = token(new Actor(2L, Role.APROVADOR, costCenterId, ApproverLevel.NIVEL_2));
        String requester = token(new Actor(3L, Role.SOLICITANTE, costCenterId, null));

        api.put(path, approver, "{\"amount\":\"5000.00\"}").assertProblem(403, "ACCESS_DENIED");
        api.put(path, requester, "{\"amount\":\"5000.00\"}").assertProblem(403, "ACCESS_DENIED");
        assertThat(budgets.count()).isZero();
    }

    @Test
    void deveCriarOrcamentoDoMesQuandoNaoExiste() {
        Response response = api.put(path, adminToken, "{\"amount\":\"15000\"}");

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("amount", "15000.00")
                .containsEntry("consumed", "0.00")
                .containsEntry("available", "15000.00");
        assertThat(storedBudget().getAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void deveAceitarValorNumericoNoJson() {
        Response response = api.put(path, adminToken, "{\"amount\":2500.5}");

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json()).containsEntry("amount", "2500.50");
    }

    @Test
    void deveAtualizarOrcamentoMantendoOConsumido() {
        budgets.save(new BudgetEntity(costCenterId, MONTH, new BigDecimal("1000.00"), new BigDecimal("400.00")));

        Response raised = api.put(path, adminToken, "{\"amount\":\"3000.00\"}");
        Response loweredToConsumed = api.put(path, adminToken, "{\"amount\":\"400.00\"}");

        assertThat(raised.json()).containsEntry("amount", "3000.00").containsEntry("available", "2600.00");
        assertThat(loweredToConsumed.status()).isEqualTo(200);
        assertThat(loweredToConsumed.json())
                .containsEntry("amount", "400.00")
                .containsEntry("consumed", "400.00")
                .containsEntry("available", "0.00");
        assertThat(budgets.count()).isEqualTo(1);
    }

    @Test
    void deveRetornar422QuandoOrcamentoFicaAbaixoDoConsumido() {
        budgets.save(new BudgetEntity(costCenterId, MONTH, new BigDecimal("1000.00"), new BigDecimal("800.00")));

        Response response = api.put(path, adminToken, "{\"amount\":\"799.99\"}");

        response.assertProblem(422, "BUDGET_BELOW_CONSUMED");
        assertThat(response.json().get("detail").toString()).contains("799.99", "800.00", "2026-11");
        BudgetEntity unchanged = storedBudget();
        assertThat(unchanged.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(unchanged.getConsumed()).isEqualByComparingTo("800.00");
    }

    @Test
    void deveRetornar400ParaValorInvalidoSemArredondar() {
        for (String body : List.of("{\"amount\":\"10.505\"}", "{\"amount\":\"-1.00\"}", "{}")) {
            Response response = api.put(path, adminToken, body);

            response.assertProblem(400, "VALIDATION_FAILED");
            assertThat(response.errorFields()).containsExactly("amount");
        }
        api.put(path, adminToken, "{\"amount\":\"dez\"}").assertProblem(400, "MALFORMED_REQUEST");
        assertThat(budgets.count()).isZero();
    }

    /**
     * O PUT baixa o orçado para 500 enquanto uma aprovação consome 600 de 1000. Seja qual for a ordem, só um dos dois
     * pode vencer, e o PUT perdedor recebe 422 (uma leitura seguida de UPDATE cairia no CHECK e devolveria 500).
     */
    @RepeatedTest(15)
    void deveImpedirOrcamentoAbaixoDoConsumidoComAprovacaoSimultanea() throws Exception {
        budgets.save(new BudgetEntity(costCenterId, MONTH, new BigDecimal("1000.00"), BigDecimal.ZERO));
        CountDownLatch start = new CountDownLatch(1);

        List<Object> results = runConcurrently(
                start,
                () -> api.put(path, adminToken, "{\"amount\":\"500.00\"}").status(),
                () -> transactions.execute(status -> budgets.consume(costCenterId, MONTH, new BigDecimal("600.00"))));

        int putStatus = (Integer) results.get(0);
        int consumedRows = (Integer) results.get(1);
        BudgetEntity budget = storedBudget();
        assertThat(putStatus).isIn(200, 422);
        assertThat(putStatus == 200).as("exatamente um vence").isNotEqualTo(consumedRows == 1);
        assertThat(budget.getConsumed()).isLessThanOrEqualTo(budget.getAmount());
        if (putStatus == 200) {
            assertThat(budget.getAmount()).isEqualByComparingTo("500.00");
            assertThat(budget.getConsumed()).isEqualByComparingTo("0.00");
        } else {
            assertThat(budget.getAmount()).isEqualByComparingTo("1000.00");
            assertThat(budget.getConsumed()).isEqualByComparingTo("600.00");
        }
    }

    @RepeatedTest(10)
    void deveCriarUmaUnicaLinhaComPutsSimultaneosNumMesSemOrcamento() throws Exception {
        CountDownLatch start = new CountDownLatch(1);

        List<Object> results = runConcurrently(
                start,
                () -> api.put(path, adminToken, "{\"amount\":\"1000.00\"}").status(),
                () -> api.put(path, adminToken, "{\"amount\":\"2000.00\"}").status());

        assertThat(results).containsExactly(200, 200);
        assertThat(budgets.count()).isEqualTo(1);
        assertThat(storedBudget().getAmount()).isIn(new BigDecimal("1000.00"), new BigDecimal("2000.00"));
    }

    private List<Object> runConcurrently(CountDownLatch start, Callable<?>... tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<?> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private BudgetEntity storedBudget() {
        return budgets.findByCostCenterIdAndMonth(costCenterId, MONTH).orElseThrow();
    }

    private String token(Actor actor) {
        return jwtService.issue(actor).value();
    }
}
