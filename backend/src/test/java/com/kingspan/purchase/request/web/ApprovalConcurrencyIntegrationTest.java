package com.kingspan.purchase.request.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.BusinessTime;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.security.JwtService;
import com.kingspan.purchase.support.ApiClient;
import com.kingspan.purchase.support.ApiClient.Response;
import com.kingspan.purchase.support.DatabaseCleaner;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Corridas reais por HTTP (sem {@code @Transactional} no teste): cada chamada roda na própria transação do service,
 * e um {@link CountDownLatch} solta todas as threads juntas para maximizar a sobreposição (ADR 001).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApprovalConcurrencyIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final int REPETITIONS = 10;
    private static final long TIMEOUT_SECONDS = 30;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CostCenterRepository costCenters;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JsonMapper json;

    private ApiClient api;
    private Long costCenterId;
    private String ownerToken;
    private String level1Token;
    private String level2Token;
    private String adminToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        ownerToken = token(saveUser("dono@example.com", Role.SOLICITANTE, null), Role.SOLICITANTE, null);
        level1Token = token(
                saveUser("nivel1@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1),
                Role.APROVADOR,
                ApproverLevel.NIVEL_1);
        level2Token = token(
                saveUser("nivel2@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2),
                Role.APROVADOR,
                ApproverLevel.NIVEL_2);
        Long adminId = users.save(
                        new UserEntity("Admin", "admin@example.com", "{bcrypt}hash", Role.ADMIN, null, null, NOW))
                .getId();
        adminToken =
                jwtService.issue(new Actor(adminId, Role.ADMIN, null, null)).value();
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @RepeatedTest(REPETITIONS)
    void deveRetornarExatamenteUm200EUm409QuandoDoisAprovadoresDecidemAMesmaEtapaAoMesmoTempo() throws Exception {
        long requestId = submitted("100.00");
        defineBudget(competenceOf(requestId), "1000.00");
        List<String> approvers = List.of(level1Token, level2Token);

        List<Response> responses = raceAll(approvers, approverToken -> approve(requestId, approverToken));

        assertThat(responses).extracting(Response::status).containsExactlyInAnyOrder(200, 409);
        responses.stream()
                .filter(response -> response.status() == 409)
                .forEach(loser -> loser.assertProblem(409, "CONCURRENT_MODIFICATION"));
        Map<String, Object> detail = detail(requestId);
        assertThat(detail).containsEntry("status", "APPROVED").containsEntry("lockVersion", 2);
        // O perdedor é desfeito por inteiro: um só consumo, uma só decisão na etapa e um só evento.
        assertThat(consumed()).isEqualByComparingTo("100.00");
        assertThat(countEvents("APPROVE")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM approval_step WHERE status = 'APPROVED' AND decided_by IS NOT NULL",
                        Long.class))
                .isEqualTo(1);
    }

    @RepeatedTest(REPETITIONS)
    void deveRespeitarOSaldoQuandoAprovacoesFinaisDeSolicitacoesDiferentesConcorremPeloMesmoOrcamento()
            throws Exception {
        // 5 × 300,00 contra 1.000,00: cabem exatamente 3, e as 2 excedentes precisam receber 422.
        List<Long> requestIds =
                IntStream.range(0, 5).mapToObj(i -> submitted("300.00")).toList();
        YearMonth competence = competenceOf(requestIds.get(0));
        defineBudget(competence, "1000.00");

        List<Response> responses = raceAll(requestIds, requestId -> approve(requestId, level1Token));

        assertThat(responses).extracting(Response::status).containsOnly(200, 422);
        List<Response> exceeded =
                responses.stream().filter(response -> response.status() == 422).toList();
        assertThat(exceeded).hasSize(2).allSatisfy(response -> {
            response.assertProblem(422, "BUDGET_EXCEEDED");
            // O perdedor só falha depois do commit do 3º vencedor, então o saldo informado é sempre o restante.
            assertThat(response.json().get("detail"))
                    .isEqualTo("O centro de custo CC-TI-01 tem R$ 100,00 disponíveis em " + competence
                            + "; a solicitação exige R$ 300,00.");
        });
        Map<String, Object> budget = jdbc.queryForMap("SELECT amount, consumed FROM budget");
        assertThat((BigDecimal) budget.get("consumed"))
                .isLessThanOrEqualTo((BigDecimal) budget.get("amount"))
                .isEqualByComparingTo("900.00");
        assertThat(requestIds)
                .extracting(requestId -> detail(requestId).get("status"))
                .containsExactlyInAnyOrder("APPROVED", "APPROVED", "APPROVED", "PENDING_APPROVAL", "PENDING_APPROVAL");
        assertThat(countEvents("APPROVE")).isEqualTo(3);
    }

    /**
     * Prepara uma thread por entrada e só então abre a largada: sem a barreira {@code ready}, as primeiras chamadas
     * poderiam terminar antes de as últimas threads existirem.
     */
    private <T> List<Response> raceAll(List<T> inputs, Function<T, Response> call) throws Exception {
        CountDownLatch ready = new CountDownLatch(inputs.size());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(inputs.size())) {
            List<Future<Response>> futures = new ArrayList<>();
            for (T input : inputs) {
                Callable<Response> task = () -> {
                    ready.countDown();
                    start.await();
                    return call.apply(input);
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return responses;
        }
    }

    /** Solicitação de um item (até 1.000,00: uma só etapa NIVEL_1) submetida pelo dono, com lockVersion 1. */
    private long submitted(String unitPrice) {
        String body = """
                {
                  "title": "Compra para o time de TI",
                  "description": "Itens para a equipe",
                  "category": "EQUIPMENT",
                  "neededBy": "%s",
                  "items": [ { "description": "Item", "quantity": 1, "unitPrice": "%s" } ]
                }
                """.formatted(LocalDate.now(BusinessTime.ZONE).plusDays(30), unitPrice);
        Response created = api.post("/requests", ownerToken, body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        long requestId = ((Number) created.json().get("id")).longValue();
        Response submitted = api.post("/requests/" + requestId + "/submit", ownerToken, "{ \"lockVersion\": 0 }");
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        return requestId;
    }

    private Response approve(long requestId, String approverToken) {
        return api.post("/requests/" + requestId + "/approve", approverToken, "{ \"lockVersion\": 1 }");
    }

    private Map<String, Object> detail(long requestId) {
        Response response = api.get("/requests/" + requestId, adminToken);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json();
    }

    private YearMonth competenceOf(long requestId) {
        return YearMonth.parse((String) detail(requestId).get("competenceMonth"));
    }

    private void defineBudget(YearMonth month, String amount) {
        jdbc.update(
                "INSERT INTO budget (cost_center_id, month, amount, consumed) VALUES (?, ?, ?, 0)",
                costCenterId,
                month.atDay(1),
                new BigDecimal(amount));
    }

    private BigDecimal consumed() {
        return jdbc.queryForObject(
                "SELECT consumed FROM budget WHERE cost_center_id = ?", BigDecimal.class, costCenterId);
    }

    private long countEvents(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM request_event WHERE action = ?", Long.class, action);
    }

    private Long saveUser(String email, Role role, ApproverLevel level) {
        return users.save(new UserEntity("Usuário", email, "{bcrypt}hash", role, level, costCenterId, NOW))
                .getId();
    }

    private String token(Long userId, Role role, ApproverLevel level) {
        return jwtService.issue(new Actor(userId, role, costCenterId, level)).value();
    }
}
