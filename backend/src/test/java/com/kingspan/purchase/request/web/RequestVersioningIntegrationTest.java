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
import com.kingspan.purchase.support.ApiClient;
import com.kingspan.purchase.support.ApiClient.Response;
import com.kingspan.purchase.support.DatabaseCleaner;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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

/** Devolução → edição → reenvio (README 2.3, item 6): nova versão, anterior imutável e cadeia recalculada do zero. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RequestVersioningIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String CHANGES_COMMENT = "Detalhar a configuração dos notebooks";
    private static final String NEEDED_BY =
            LocalDate.now(BusinessTime.ZONE).plusDays(30).toString();

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
    private Long level1Id;
    private Long level2Id;
    private Long adminId;
    private String ownerToken;
    private String level1Token;
    private String level2Token;
    private String adminToken;
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
        Long ownerId = saveUser("dono@example.com", Role.SOLICITANTE, null, costCenterId);
        ownerToken = token(ownerId, Role.SOLICITANTE, null, costCenterId);
        level1Id = saveUser("nivel1@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1, costCenterId);
        level1Token = token(level1Id, Role.APROVADOR, ApproverLevel.NIVEL_1, costCenterId);
        level2Id = saveUser("nivel2@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, costCenterId);
        level2Token = token(level2Id, Role.APROVADOR, ApproverLevel.NIVEL_2, costCenterId);
        adminId = saveUser("admin@example.com", Role.ADMIN, null, null);
        adminToken = token(adminId, Role.ADMIN, null, null);
        otherCostCenterApproverToken = token(
                saveUser("rh@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, otherCostCenterId),
                Role.APROVADOR,
                ApproverLevel.NIVEL_2,
                otherCostCenterId);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveGerarV2NoReenvioPreservandoAV1SemContarAsAprovacoesDela() {
        long requestId = submitted("Notebooks para o time", 2, "2500.00");
        // NIVEL_2 aprova a etapa NIVEL_1 da v1: se essa aprovação contasse na v2, ele não poderia aprovar a NIVEL_2.
        assertOk(decide("approve", requestId, level2Token, 1, null));
        assertOk(decide("request-changes", requestId, adminToken, 2, CHANGES_COMMENT));
        Map<String, Object> v1Before = version(requestId, 1, ownerToken);

        Response edited = edit(requestId, 3, "Notebooks com 32 GB", 2, "3000.00");
        assertThat(edited.json()).containsEntry("version", 2).containsEntry("status", "CHANGES_REQUESTED");
        Response resubmitted = submit(requestId, 4);

        assertOk(resubmitted);
        assertThat(resubmitted.json())
                .containsEntry("version", 2)
                .containsEntry("status", "PENDING_APPROVAL")
                .containsEntry("total", "6000.00")
                .containsEntry("lockVersion", 5);
        assertThat(steps(resubmitted.json()))
                .extracting(step -> step.get("level"), step -> step.get("status"), step -> step.get("decidedBy"))
                .containsExactly(tuple("NIVEL_1", "PENDING", null), tuple("NIVEL_2", "PENDING", null));
        assertThat(detail(requestId, level1Token).get("allowedActions"))
                .isEqualTo(List.of("APPROVE", "REJECT", "REQUEST_CHANGES"));

        defineBudget(YearMonth.parse((String) resubmitted.json().get("competenceMonth")), "6000.00");
        assertOk(decide("approve", requestId, level1Token, 5, null));
        Response approved = decide("approve", requestId, level2Token, 6, null);

        assertOk(approved);
        assertThat(approved.json()).containsEntry("status", "APPROVED").containsEntry("version", 2);
        assertThat(steps(approved.json()))
                .extracting(step -> step.get("decidedBy"))
                .containsExactly(level1Id.intValue(), level2Id.intValue());
        assertThat(consumed()).isEqualByComparingTo("6000.00");
        Map<String, Object> v1After = version(requestId, 1, ownerToken);
        // Só o "current" muda: conteúdo, itens, submissão e etapas da v1 são os mesmos de antes do reenvio.
        assertThat(v1Before).containsEntry("current", true);
        assertThat(v1After).usingRecursiveComparison().ignoringFields("current").isEqualTo(v1Before);
        assertThat(v1After)
                .containsEntry("current", false)
                .containsEntry("title", "Notebooks para o time")
                .containsEntry("total", "5000.00");
        assertThat(steps(v1After))
                .extracting(step -> step.get("level"), step -> step.get("status"), step -> step.get("decidedBy"))
                .containsExactly(
                        tuple("NIVEL_1", "APPROVED", level2Id.intValue()),
                        tuple("NIVEL_2", "CHANGES_REQUESTED", adminId.intValue()));
    }

    @Test
    void deveRecalcularACadeiaDoZeroPeloNovoTotalDaV2() {
        long requestId = submitted("Notebooks para o time", 2, "2500.00");
        assertOk(decide("approve", requestId, level1Token, 1, null));
        assertOk(decide("request-changes", requestId, level2Token, 2, CHANGES_COMMENT));

        edit(requestId, 3, "Só um monitor", 1, "800.00");
        Response resubmitted = submit(requestId, 4);

        assertOk(resubmitted);
        assertThat(steps(resubmitted.json()))
                .extracting(step -> step.get("level"), step -> step.get("status"))
                .containsExactly(tuple("NIVEL_1", "PENDING"));
        assertThat(steps(version(requestId, 1, ownerToken)))
                .extracting(step -> step.get("level"), step -> step.get("status"))
                .containsExactly(tuple("NIVEL_1", "APPROVED"), tuple("NIVEL_2", "CHANGES_REQUESTED"));

        // Quem aprovou a NIVEL_1 da v1 precisa aprovar de novo: a etapa da v2 começa pendente.
        defineBudget(YearMonth.parse((String) resubmitted.json().get("competenceMonth")), "800.00");
        Response approved = decide("approve", requestId, level1Token, 5, null);

        assertOk(approved);
        assertThat(approved.json()).containsEntry("status", "APPROVED");
        assertThat(consumed()).isEqualByComparingTo("800.00");
    }

    @Test
    void deveListarAsVersoesIndicandoAVersaoDeTrabalhoAindaNaoSubmetida() {
        long requestId = changesRequested();

        edit(requestId, 3, "Notebooks com 32 GB", 2, "3000.00");
        List<Map<String, Object>> whileEditing = versions(requestId, ownerToken);
        submit(requestId, 4);
        List<Map<String, Object>> afterResubmit = versions(requestId, level1Token);

        assertThat(whileEditing)
                .extracting(
                        version -> version.get("version"),
                        version -> version.get("current"),
                        version -> version.get("total"),
                        version -> version.get("submittedAt") != null)
                .containsExactly(tuple(1, false, "5000.00", true), tuple(2, true, "6000.00", false));
        assertThat(afterResubmit)
                .extracting(
                        version -> version.get("version"),
                        version -> version.get("title"),
                        version -> version.get("submittedAt") != null)
                .containsExactly(tuple(1, "Notebooks para o time", true), tuple(2, "Notebooks com 32 GB", true));
        assertThat(afterResubmit.get(1).get("competenceMonth")).isNotNull();
    }

    @Test
    void deveDetalharUmaVersaoComItensEEtapas() {
        long requestId = changesRequested();
        edit(requestId, 3, "Notebooks com 32 GB", 2, "3000.00");
        submit(requestId, 4);

        Map<String, Object> v2 = version(requestId, 2, adminToken);

        assertThat(v2)
                .containsEntry("requestId", (int) requestId)
                .containsEntry("version", 2)
                .containsEntry("current", true)
                .containsEntry("title", "Notebooks com 32 GB")
                .containsEntry("category", "EQUIPMENT")
                .containsEntry("total", "6000.00")
                .doesNotContainKey("allowedActions");
        assertThat(v2.get("submittedAt")).isNotNull();
        assertThat(items(v2))
                .singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("quantity", 2)
                        .containsEntry("unitPrice", "3000.00")
                        .containsEntry("subtotal", "6000.00"));
        assertThat(items(version(requestId, 1, adminToken)))
                .singleElement()
                .satisfies(item -> assertThat(item).containsEntry("unitPrice", "2500.00"));
    }

    @Test
    void deveCriarV3NaSegundaDevolucaoPreservandoAsVersoesAnteriores() {
        long requestId = changesRequested();
        submit(requestId, 3);
        assertOk(decide("request-changes", requestId, level1Token, 4, CHANGES_COMMENT));

        edit(requestId, 5, "Terceira tentativa", 1, "900.00");
        Response resubmitted = submit(requestId, 6);

        assertOk(resubmitted);
        assertThat(resubmitted.json()).containsEntry("version", 3);
        assertThat(versions(requestId, ownerToken))
                .extracting(version -> version.get("version"), version -> version.get("total"))
                .containsExactly(tuple(1, "5000.00"), tuple(2, "5000.00"), tuple(3, "900.00"));
        assertThat(steps(version(requestId, 2, ownerToken)))
                .extracting(step -> step.get("status"))
                .containsExactly("CHANGES_REQUESTED", "PENDING");
        assertThat(jdbc.queryForList(
                        "SELECT version_number FROM request_event WHERE action = 'SUBMIT' ORDER BY id", Integer.class))
                .containsExactly(1, 2, 3);
    }

    @Test
    void deveRetornar404ParaVersaoInexistenteOuForaDaVisibilidade() {
        long requestId = changesRequested();

        api.get("/requests/" + requestId + "/versions/2", ownerToken).assertProblem(404, "NOT_FOUND");
        api.get("/requests/" + requestId + "/versions/0", ownerToken).assertProblem(404, "NOT_FOUND");
        api.get("/requests/" + requestId + "/versions", otherCostCenterApproverToken)
                .assertProblem(404, "NOT_FOUND");
        api.get("/requests/" + requestId + "/versions/1", otherCostCenterApproverToken)
                .assertProblem(404, "NOT_FOUND");
        api.get("/requests/999999/versions", adminToken).assertProblem(404, "NOT_FOUND");
    }

    @Test
    void deveRetornar404ParaVersoesDeRascunhoDeOutroUsuario() {
        Response created = api.post("/requests", ownerToken, body("Rascunho", 1, "100.00", null));
        long requestId = ((Number) created.json().get("id")).longValue();

        assertThat(versions(requestId, ownerToken)).hasSize(1);
        api.get("/requests/" + requestId + "/versions", adminToken).assertProblem(404, "NOT_FOUND");
        api.get("/requests/" + requestId + "/versions/1", level1Token).assertProblem(404, "NOT_FOUND");
    }

    @Test
    void deveRetornar400ParaNumeroDeVersaoInvalido() {
        long requestId = changesRequested();

        Response response = api.get("/requests/" + requestId + "/versions/abc", ownerToken);

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly("versionNumber");
    }

    @Test
    void deveRetornar401SemToken() {
        api.get("/requests/1/versions", null).assertProblem(401, "UNAUTHORIZED");
    }

    /** v1 de 5.000,00 (NIVEL_1 e NIVEL_2) devolvida na NIVEL_2 depois da aprovação da NIVEL_1; lockVersion 3. */
    private long changesRequested() {
        long requestId = submitted("Notebooks para o time", 2, "2500.00");
        assertOk(decide("approve", requestId, level1Token, 1, null));
        assertOk(decide("request-changes", requestId, level2Token, 2, CHANGES_COMMENT));
        return requestId;
    }

    /** Solicitação de um item criada e submetida pelo dono (lockVersion 1). */
    private long submitted(String title, int quantity, String unitPrice) {
        Response created = api.post("/requests", ownerToken, body(title, quantity, unitPrice, null));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        long requestId = ((Number) created.json().get("id")).longValue();
        assertOk(submit(requestId, 0));
        return requestId;
    }

    private Response edit(long requestId, long lockVersion, String title, int quantity, String unitPrice) {
        Response response =
                api.put("/requests/" + requestId, ownerToken, body(title, quantity, unitPrice, lockVersion));
        assertOk(response);
        return response;
    }

    private Response submit(long requestId, long lockVersion) {
        return api.post("/requests/" + requestId + "/submit", ownerToken, "{ \"lockVersion\": " + lockVersion + " }");
    }

    private Response decide(String action, long requestId, String token, long lockVersion, String comment) {
        String body = comment == null
                ? "{ \"lockVersion\": " + lockVersion + " }"
                : "{ \"lockVersion\": " + lockVersion + ", \"comment\": \"" + comment + "\" }";
        return api.post("/requests/" + requestId + "/" + action, token, body);
    }

    private static String body(String title, int quantity, String unitPrice, Long lockVersion) {
        String lock = lockVersion == null ? "" : ", \"lockVersion\": " + lockVersion;
        return """
                {
                  "title": "%s",
                  "description": "Equipamentos para a equipe",
                  "category": "EQUIPMENT",
                  "neededBy": "%s",
                  "items": [ { "description": "Notebook", "quantity": %d, "unitPrice": "%s" } ]%s
                }
                """.formatted(title, NEEDED_BY, quantity, unitPrice, lock);
    }

    private Map<String, Object> detail(long requestId, String token) {
        Response response = api.get("/requests/" + requestId, token);
        assertOk(response);
        return response.json();
    }

    private List<Map<String, Object>> versions(long requestId, String token) {
        Response response = api.get("/requests/" + requestId + "/versions", token);
        assertOk(response);
        return response.jsonList();
    }

    private Map<String, Object> version(long requestId, int number, String token) {
        Response response = api.get("/requests/" + requestId + "/versions/" + number, token);
        assertOk(response);
        return response.json();
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

    private static void assertOk(Response response) {
        assertThat(response.status()).as(response.body()).isEqualTo(200);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("steps");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("items");
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
