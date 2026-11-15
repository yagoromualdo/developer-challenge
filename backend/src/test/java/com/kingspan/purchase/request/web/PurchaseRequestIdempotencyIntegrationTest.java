package com.kingspan.purchase.request.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.BusinessTime;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.security.JwtService;
import com.kingspan.purchase.support.ApiClient;
import com.kingspan.purchase.support.ApiClient.Response;
import com.kingspan.purchase.support.DatabaseCleaner;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PurchaseRequestIdempotencyIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String REPLAYED = PurchaseRequestController.IDEMPOTENT_REPLAYED;

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
    private String colleagueToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        ownerToken = requesterToken("dono@example.com");
        colleagueToken = requesterToken("colega@example.com");
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveDevolverAMesmaRespostaSemCriarOutraSolicitacaoQuandoChaveEPayloadSeRepetem() {
        String key = newKey();

        Response first = create(ownerToken, key, body("Notebooks"));
        Response second = create(ownerToken, key, body("Notebooks"));

        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(first.headers().getFirst(REPLAYED)).isEqualTo("false");
        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(second.headers().getFirst(REPLAYED)).isEqualTo("true");
        assertThat(second.headers().getLocation()).isEqualTo(first.headers().getLocation());
        assertThat(second.json()).isEqualTo(first.json());
        assertSingleCreation();
    }

    @Test
    void deveTratarCorposEquivalentesComoOMesmoPayload() {
        String key = newKey();
        Response first = create(ownerToken, key, body("Notebooks"));

        // Outra ordem de campos, sem espaços, total enviado (ignorado) e preços em outra grafia.
        String equivalent = """
                {"items":[{"unitPrice":4899.9,"quantity":5,"description":"Notebook 16GB/512GB"},\
                {"quantity":2,"unitPrice":"89.95","description":"Mouse"}],"total":"1.00",\
                "neededBy":"%s","category":"EQUIPMENT",\
                "description":"Substituição de 5 equipamentos","title":"Notebooks"}""".formatted(neededBy());
        Response second = create(ownerToken, key, equivalent);

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(second.headers().getFirst(REPLAYED)).isEqualTo("true");
        assertThat(second.json()).containsEntry("id", first.json().get("id"));
        assertSingleCreation();
    }

    @Test
    void deveRetornar409QuandoAChaveEReutilizadaComOutroPayload() {
        String key = newKey();
        create(ownerToken, key, body("Notebooks"));

        Response conflict = create(ownerToken, key, body("Monitores"));

        conflict.assertProblem(409, "IDEMPOTENCY_KEY_REUSED");
        assertThat(conflict.json().get("detail").toString()).contains("já foi usada com outro conteúdo");
        assertSingleCreation();
    }

    @Test
    void deveIsolarAChavePorUsuario() {
        String key = newKey();

        Response mine = create(ownerToken, key, body("Notebooks"));
        Response colleague = create(colleagueToken, key, body("Notebooks"));

        assertThat(colleague.status()).as(colleague.body()).isEqualTo(201);
        assertThat(colleague.headers().getFirst(REPLAYED)).isEqualTo("false");
        assertThat(colleague.json().get("id")).isNotEqualTo(mine.json().get("id"));
        assertThat(count("purchase_request")).isEqualTo(2);
        assertThat(count("idempotency_key")).isEqualTo(2);
    }

    @Test
    void deveCriarNovaSolicitacaoQuandoAChaveSalvaJaExpirou() {
        String key = newKey();
        Response first = create(ownerToken, key, body("Notebooks"));
        jdbc.update("UPDATE idempotency_key SET created_at = now() - interval '25 hours'");

        Response second = create(ownerToken, key, body("Monitores"));

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(second.headers().getFirst(REPLAYED)).isEqualTo("false");
        assertThat(second.json().get("id")).isNotEqualTo(first.json().get("id"));
        assertThat(count("purchase_request")).isEqualTo(2);
        assertThat(count("idempotency_key")).isEqualTo(1);
    }

    @Test
    void naoDeveGuardarAChaveQuandoACriacaoFalha() {
        String key = newKey();
        String otherCostCenter = body("Notebooks").replaceFirst("\\{", "{ \"costCenterId\": 999,");

        create(ownerToken, key, otherCostCenter).assertProblem(422, "COST_CENTER_NOT_ALLOWED");
        assertThat(count("idempotency_key")).isZero();

        // Sem resposta salva, o cliente corrige o corpo e repete com a mesma chave.
        Response retry = create(ownerToken, key, body("Notebooks"));
        assertThat(retry.status()).as(retry.body()).isEqualTo(201);
        assertSingleCreation();
    }

    @Test
    void deveRetornar400QuandoAChaveEInvalida() {
        Response response = create(ownerToken, "x".repeat(256), body("Notebooks"));

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly(PurchaseRequestController.IDEMPOTENCY_KEY);
        assertThat(count("purchase_request")).isZero();
    }

    @Test
    void deveCriarUmaSolicitacaoPorChamadaQuandoNaoHaChave() {
        api.post("/requests", ownerToken, body("Notebooks"));
        api.post("/requests", ownerToken, body("Notebooks"));

        assertThat(count("purchase_request")).isEqualTo(2);
        assertThat(count("idempotency_key")).isZero();
    }

    @RepeatedTest(10)
    void deveCriarUmaUnicaSolicitacaoQuandoDuasRequisicoesSimultaneasUsamAMesmaChave() throws Exception {
        String key = newKey();
        String payload = body("Notebooks");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Response>> calls = List.of(
                    executor.submit(() -> createWhenReleased(ready, start, key, payload)),
                    executor.submit(() -> createWhenReleased(ready, start, key, payload)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Response a = calls.get(0).get(30, TimeUnit.SECONDS);
            Response b = calls.get(1).get(30, TimeUnit.SECONDS);

            assertThat(a.status()).as(a.body()).isEqualTo(201);
            assertThat(b.status()).as(b.body()).isEqualTo(201);
            assertThat(b.json()).isEqualTo(a.json());
            assertThat(List.of(a.headers().getFirst(REPLAYED), b.headers().getFirst(REPLAYED)))
                    .containsExactlyInAnyOrder("false", "true");
            assertSingleCreation();
        } finally {
            executor.shutdownNow();
        }
    }

    private Response createWhenReleased(CountDownLatch ready, CountDownLatch start, String key, String payload)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return create(ownerToken, key, payload);
    }

    private void assertSingleCreation() {
        assertThat(count("purchase_request")).isEqualTo(1);
        assertThat(count("request_version")).isEqualTo(1);
        assertThat(count("request_event")).isEqualTo(1);
        assertThat(count("idempotency_key")).isEqualTo(1);
    }

    private Response create(String token, String key, String body) {
        return api.post("/requests", token, body, Map.of(PurchaseRequestController.IDEMPOTENCY_KEY, key));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String body(String title) {
        return """
                {
                  "title": "%s",
                  "description": "Substituição de 5 equipamentos",
                  "category": "EQUIPMENT",
                  "neededBy": "%s",
                  "items": [
                    { "description": "Notebook 16GB/512GB", "quantity": 5, "unitPrice": "4899.90" },
                    { "description": "Mouse", "quantity": 2, "unitPrice": 89.95 }
                  ]
                }
                """.formatted(title, neededBy());
    }

    private static LocalDate neededBy() {
        return LocalDate.now(BusinessTime.ZONE).plusDays(30);
    }

    private String requesterToken(String email) {
        Long userId = users.save(
                        new UserEntity("Usuário", email, "{bcrypt}hash", Role.SOLICITANTE, null, costCenterId, NOW))
                .getId();
        return jwtService
                .issue(new Actor(userId, Role.SOLICITANTE, costCenterId, null))
                .value();
    }
}
