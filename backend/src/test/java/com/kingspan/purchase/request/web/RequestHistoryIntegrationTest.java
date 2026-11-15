package com.kingspan.purchase.request.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.BusinessTime;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.security.JwtService;
import com.kingspan.purchase.shared.web.RequestId;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RequestHistoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String CHANGES_COMMENT = "Faltou o orçamento do fornecedor";

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
    private Long ownerId;
    private Long approverId;
    private String ownerToken;
    private String approverToken;
    private String adminToken;
    private String otherOwnerToken;
    private String otherCostCenterApproverToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        Long otherCostCenterId = costCenters
                .save(new CostCenterEntity("CC-RH-01", "Recursos Humanos", NOW))
                .getId();
        ownerId = saveUser("dono@example.com", Role.SOLICITANTE, null, costCenterId);
        ownerToken = token(ownerId, Role.SOLICITANTE, null, costCenterId);
        approverId = saveUser("nivel1@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1, costCenterId);
        approverToken = token(approverId, Role.APROVADOR, ApproverLevel.NIVEL_1, costCenterId);
        Long adminId = saveUser("admin@example.com", Role.ADMIN, null, null);
        adminToken = token(adminId, Role.ADMIN, null, null);
        Long otherOwnerId = saveUser("outro@example.com", Role.SOLICITANTE, null, costCenterId);
        otherOwnerToken = token(otherOwnerId, Role.SOLICITANTE, null, costCenterId);
        Long otherApproverId = saveUser("rh@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, otherCostCenterId);
        otherCostCenterApproverToken = token(otherApproverId, Role.APROVADOR, ApproverLevel.NIVEL_2, otherCostCenterId);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveListarTodosOsEventosEmOrdemComOsCamposExigidos() {
        List<String> httpRequestIds = new ArrayList<>();
        Response created = track(httpRequestIds, api.post("/requests", ownerToken, body(1, null)));
        long requestId = ((Number) created.json().get("id")).longValue();
        Response submitted = track(httpRequestIds, submit(requestId, lockVersion(created)));
        Response returned = track(
                httpRequestIds,
                api.post(
                        "/requests/" + requestId + "/request-changes",
                        approverToken,
                        "{ \"lockVersion\": " + lockVersion(submitted) + ", \"comment\": \"" + CHANGES_COMMENT
                                + "\" }"));
        Response edited =
                track(httpRequestIds, api.put("/requests/" + requestId, ownerToken, body(2, lockVersion(returned))));
        Response resubmitted = track(httpRequestIds, submit(requestId, lockVersion(edited)));
        defineBudget(YearMonth.parse((String) resubmitted.json().get("competenceMonth")), "1000.00");
        track(
                httpRequestIds,
                api.post(
                        "/requests/" + requestId + "/approve",
                        approverToken,
                        "{ \"lockVersion\": " + lockVersion(resubmitted) + " }"));

        List<Map<String, Object>> events = history(requestId, ownerToken);

        assertThat(events)
                .extracting(
                        event -> event.get("action"),
                        event -> event.get("fromStatus"),
                        event -> event.get("toStatus"),
                        event -> event.get("version"),
                        event -> event.get("stepLevel"),
                        event -> event.get("actorRole"))
                .containsExactly(
                        tuple("CREATE", null, "DRAFT", 1, null, "SOLICITANTE"),
                        tuple("SUBMIT", "DRAFT", "PENDING_APPROVAL", 1, null, "SOLICITANTE"),
                        tuple("REQUEST_CHANGES", "PENDING_APPROVAL", "CHANGES_REQUESTED", 1, "NIVEL_1", "APROVADOR"),
                        tuple("EDIT", "CHANGES_REQUESTED", "CHANGES_REQUESTED", 2, null, "SOLICITANTE"),
                        tuple("SUBMIT", "CHANGES_REQUESTED", "PENDING_APPROVAL", 2, null, "SOLICITANTE"),
                        tuple("APPROVE", "PENDING_APPROVAL", "APPROVED", 2, "NIVEL_1", "APROVADOR"));
        assertThat(events).extracting(event -> event.get("requestId")).containsExactlyElementsOf(httpRequestIds);
        assertThat(events)
                .extracting(event -> ((Number) event.get("actorId")).longValue())
                .containsExactly(ownerId, ownerId, approverId, ownerId, ownerId, approverId);
        assertThat(events)
                .extracting(event -> event.get("comment"))
                .containsExactly(null, null, CHANGES_COMMENT, null, null, null);
        assertThat(events).allSatisfy(event -> {
            assertThat(event)
                    .containsOnlyKeys(
                            "id",
                            "version",
                            "actorId",
                            "actorRole",
                            "action",
                            "fromStatus",
                            "toStatus",
                            "stepLevel",
                            "comment",
                            "occurredAt",
                            "requestId");
            assertThat((String) event.get("occurredAt")).endsWith("Z");
        });
        assertThat(events)
                .extracting(event -> Instant.parse((String) event.get("occurredAt")))
                .isSorted();
    }

    @Test
    void deveMostrarOMesmoHistoricoParaQuemVeASolicitacao() {
        long requestId = submittedRequest();

        assertThat(history(requestId, approverToken)).hasSize(2);
        assertThat(history(requestId, adminToken)).hasSize(2);
    }

    @Test
    void deveRetornar404ParaSolicitacaoInexistenteOuForaDaVisibilidade() {
        long requestId = submittedRequest();

        api.get("/requests/" + requestId + "/history", otherOwnerToken).assertProblem(404, "NOT_FOUND");
        api.get("/requests/" + requestId + "/history", otherCostCenterApproverToken)
                .assertProblem(404, "NOT_FOUND");
        api.get("/requests/999999/history", adminToken).assertProblem(404, "NOT_FOUND");
    }

    @Test
    void deveRetornar404ParaRascunhoDeOutroUsuario() {
        Response created = api.post("/requests", ownerToken, body(1, null));
        long requestId = ((Number) created.json().get("id")).longValue();

        assertThat(history(requestId, ownerToken)).hasSize(1);
        api.get("/requests/" + requestId + "/history", approverToken).assertProblem(404, "NOT_FOUND");
        api.get("/requests/" + requestId + "/history", adminToken).assertProblem(404, "NOT_FOUND");
    }

    @Test
    void deveRetornar401SemToken() {
        api.get("/requests/1/history", null).assertProblem(401, "UNAUTHORIZED");
    }

    private long submittedRequest() {
        Response created = api.post("/requests", ownerToken, body(1, null));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        long requestId = ((Number) created.json().get("id")).longValue();
        Response submitted = submit(requestId, lockVersion(created));
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        return requestId;
    }

    private List<Map<String, Object>> history(long requestId, String token) {
        Response response = api.get("/requests/" + requestId + "/history", token);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.jsonList();
    }

    private static Response track(List<String> httpRequestIds, Response response) {
        assertThat(response.status()).as(response.body()).isBetween(200, 201);
        httpRequestIds.add(response.headers().getFirst(RequestId.HEADER));
        return response;
    }

    private Response submit(long requestId, long lockVersion) {
        return api.post("/requests/" + requestId + "/submit", ownerToken, "{ \"lockVersion\": " + lockVersion + " }");
    }

    private static long lockVersion(Response response) {
        return ((Number) response.json().get("lockVersion")).longValue();
    }

    private static String body(int quantity, Long lockVersion) {
        String lock = lockVersion == null ? "" : ", \"lockVersion\": " + lockVersion;
        return """
                {
                  "title": "Compra para o time de TI",
                  "description": "Itens para a equipe",
                  "category": "EQUIPMENT",
                  "neededBy": "%s",
                  "items": [ { "description": "Item", "quantity": %d, "unitPrice": "300.00" } ]%s
                }
                """.formatted(LocalDate.now(BusinessTime.ZONE).plusDays(30), quantity, lock);
    }

    private void defineBudget(YearMonth month, String amount) {
        jdbc.update(
                "INSERT INTO budget (cost_center_id, month, amount, consumed) VALUES (?, ?, ?, 0)",
                costCenterId,
                month.atDay(1),
                new BigDecimal(amount));
    }

    private Long saveUser(String email, Role role, ApproverLevel level, Long userCostCenterId) {
        return users.save(new UserEntity("Usuário", email, "{bcrypt}hash", role, level, userCostCenterId, NOW))
                .getId();
    }

    private String token(Long userId, Role role, ApproverLevel level, Long userCostCenterId) {
        return jwtService
                .issue(new Actor(userId, role, userCostCenterId, level))
                .value();
    }
}
