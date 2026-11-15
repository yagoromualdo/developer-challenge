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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApprovalIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String VALID_COMMENT = "Quantidade acima do necessário para o trimestre";

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
    void deveAprovarAsTresEtapasAteApprovedConsumindoOOrcamentoNaUltima() {
        long requestId = submitted(ownerToken, "12000.00");
        defineBudget(competenceOf(requestId), "20000.00");

        Response first = approve(requestId, level1Token, 1, "De acordo");
        assertThat(first.status()).as(first.body()).isEqualTo(200);
        assertThat(first.json())
                .containsEntry("status", "PENDING_APPROVAL")
                .containsEntry("lockVersion", 2)
                .containsEntry("allowedActions", List.of());
        assertThat(consumed()).isEqualByComparingTo("0.00");

        Response second = approve(requestId, level2Token, 2, null);
        assertThat(second.status()).as(second.body()).isEqualTo(200);
        assertThat(second.json()).containsEntry("status", "PENDING_APPROVAL");
        assertThat(consumed()).isEqualByComparingTo("0.00");

        Response last = approve(requestId, adminToken, 3, null);
        assertThat(last.status()).as(last.body()).isEqualTo(200);
        assertThat(last.json())
                .containsEntry("status", "APPROVED")
                .containsEntry("lockVersion", 4)
                .containsEntry("allowedActions", List.of());
        assertThat(steps(last))
                .extracting(step -> step.get("level"), step -> step.get("status"), step -> step.get("decidedBy"))
                .containsExactly(
                        tuple("NIVEL_1", "APPROVED", level1Id.intValue()),
                        tuple("NIVEL_2", "APPROVED", level2Id.intValue()),
                        tuple("ADMIN", "APPROVED", adminId.intValue()));
        assertThat(steps(last).get(0)).containsEntry("comment", "De acordo");
        assertThat(steps(last))
                .allSatisfy(step -> assertThat(step.get("decidedAt")).isNotNull());
        assertThat(consumed()).isEqualByComparingTo("12000.00");
    }

    @Test
    void deveRegistrarUmEventoPorAprovacaoComEtapaOrigemEDestino() {
        long requestId = submitted(ownerToken, "5000.00");
        defineBudget(competenceOf(requestId), "5000.00");

        approve(requestId, level1Token, 1, null);
        Response last = approve(requestId, level2Token, 2, "  Dentro do planejado  ");

        List<Map<String, Object>> events =
                jdbc.queryForList("SELECT * FROM request_event WHERE action = 'APPROVE' ORDER BY id");
        assertThat(events)
                .extracting(
                        event -> event.get("step_level"),
                        event -> event.get("from_status"),
                        event -> event.get("to_status"),
                        event -> event.get("actor_role"))
                .containsExactly(
                        tuple("NIVEL_1", "PENDING_APPROVAL", "PENDING_APPROVAL", "APROVADOR"),
                        tuple("NIVEL_2", "PENDING_APPROVAL", "APPROVED", "APROVADOR"));
        assertThat(events.get(1))
                .containsEntry("request_id", requestId)
                .containsEntry("version_number", 1)
                .containsEntry("actor_id", level2Id)
                .containsEntry("comment", "Dentro do planejado")
                .containsEntry("http_request_id", last.headers().getFirst(RequestId.HEADER));
        assertThat(consumed()).isEqualByComparingTo("5000.00");
    }

    @Test
    void deveRetornar422BudgetExceededMantendoASolicitacaoPendente() {
        long requestId = submitted(ownerToken, "100.00");
        YearMonth competence = competenceOf(requestId);
        defineBudget(competence, "1050.00");
        jdbc.update("UPDATE budget SET consumed = 1000.00");

        Response response = approve(requestId, level1Token, 1, null);

        response.assertProblem(422, "BUDGET_EXCEEDED");
        assertThat(response.json().get("detail"))
                .isEqualTo("O centro de custo CC-TI-01 tem R$ 50,00 disponíveis em " + competence
                        + "; a solicitação exige R$ 100,00.");
        Map<String, Object> detail = detail(requestId, level1Token);
        assertThat(detail)
                .containsEntry("status", "PENDING_APPROVAL")
                .containsEntry("lockVersion", 1)
                .containsEntry("allowedActions", List.of("APPROVE", "REJECT", "REQUEST_CHANGES"));
        assertThat(steps(detail))
                .singleElement()
                .satisfies(step ->
                        assertThat(step).containsEntry("status", "PENDING").containsEntry("decidedBy", null));
        assertThat(consumed()).isEqualByComparingTo("1000.00");
        assertThat(countEvents("APPROVE")).isZero();
    }

    @Test
    void deveRetornar422BudgetExceededQuandoOMesNaoTemOrcamento() {
        long requestId = submitted(ownerToken, "100.00");

        Response response = approve(requestId, level1Token, 1, null);

        response.assertProblem(422, "BUDGET_EXCEEDED");
        assertThat(response.json().get("detail"))
                .isEqualTo("O centro de custo CC-TI-01 tem R$ 0,00 disponíveis em " + competenceOf(requestId)
                        + "; a solicitação exige R$ 100,00.");
        assertThat(detail(requestId, ownerToken)).containsEntry("status", "PENDING_APPROVAL");
    }

    @Test
    void naoDeveConsumirOrcamentoAoAprovarEtapaIntermediaria() {
        long requestId = submitted(ownerToken, "5000.00");

        Response response = approve(requestId, level1Token, 1, null);

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json()).containsEntry("status", "PENDING_APPROVAL");
    }

    @Test
    void deveRetornar422SelfApprovalQuandoAprovadorDecideOProprioPedido() {
        long requestId = submitted(level2Token, "100.00");
        defineBudget(competenceOf(requestId), "1000.00");

        Response response = approve(requestId, level2Token, 1, null);

        response.assertProblem(422, "SELF_APPROVAL_FORBIDDEN");
        assertThat(response.json().get("detail"))
                .isEqualTo("Não é possível aprovar a solicitação: você é o dono desta solicitação, e ninguém pode"
                        + " decidir o próprio pedido (segregação de funções).");
        assertThat(detail(requestId, level2Token).get("allowedActions")).isEqualTo(List.of("CANCEL"));
    }

    @Test
    void deveRetornar422DuplicateApproverQuandoOMesmoAprovadorTentaDuasEtapas() {
        long requestId = submitted(ownerToken, "5000.00");
        assertThat(approve(requestId, level2Token, 1, null).status()).isEqualTo(200);

        Response response = approve(requestId, level2Token, 2, null);

        response.assertProblem(422, "DUPLICATE_APPROVER");
        assertThat(response.json().get("detail"))
                .isEqualTo("Não é possível aprovar a solicitação: você já aprovou a etapa NIVEL_1 desta versão"
                        + " (segregação de funções).");
        assertThat(detail(requestId, level2Token).get("allowedActions")).isEqualTo(List.of());
    }

    @Test
    void deveRetornar422ApproverNotEligibleQuandoNivel1TentaAEtapaNivel2() {
        long requestId = submitted(ownerToken, "5000.00");
        assertThat(approve(requestId, adminToken, 1, null).status()).isEqualTo(200);

        approve(requestId, level1Token, 2, null).assertProblem(422, "APPROVER_NOT_ELIGIBLE");
        reject(requestId, level1Token, 2, VALID_COMMENT).assertProblem(422, "APPROVER_NOT_ELIGIBLE");
    }

    @Test
    void deveRetornar422ApproverNotEligibleQuandoAprovadorTentaAEtapaAdmin() {
        long requestId = submitted(ownerToken, "12000.00");
        approve(requestId, level1Token, 1, null);
        approve(requestId, level2Token, 2, null);

        Response response = approve(requestId, level2Token, 3, null);

        response.assertProblem(422, "APPROVER_NOT_ELIGIBLE");
        assertThat(response.json().get("detail").toString()).contains("a etapa atual é ADMIN e exige um ADMIN");
    }

    @Test
    void deveRejeitarComComentarioGravandoEtapaEEvento() {
        long requestId = submitted(ownerToken, "100.00");

        Response response = reject(requestId, level1Token, 1, VALID_COMMENT);

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("status", "REJECTED")
                .containsEntry("lockVersion", 2)
                .containsEntry("allowedActions", List.of());
        assertThat(steps(response))
                .singleElement()
                .satisfies(step -> assertThat(step)
                        .containsEntry("status", "REJECTED")
                        .containsEntry("decidedBy", level1Id.intValue())
                        .containsEntry("comment", VALID_COMMENT));
        assertThat(jdbc.queryForMap("SELECT * FROM request_event WHERE action = 'REJECT'"))
                .containsEntry("from_status", "PENDING_APPROVAL")
                .containsEntry("to_status", "REJECTED")
                .containsEntry("step_level", "NIVEL_1")
                .containsEntry("comment", VALID_COMMENT);
        assertThat(detail(requestId, ownerToken).get("allowedActions")).isEqualTo(List.of());
    }

    @Test
    void deveDevolverParaAjusteLiberandoEdicaoESubmissaoAoDono() {
        long requestId = submitted(ownerToken, "5000.00");
        approve(requestId, level1Token, 1, null);

        Response response = requestChanges(requestId, level2Token, 2, VALID_COMMENT);

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json()).containsEntry("status", "CHANGES_REQUESTED");
        assertThat(steps(response))
                .extracting(step -> step.get("status"))
                .containsExactly("APPROVED", "CHANGES_REQUESTED");
        assertThat(jdbc.queryForMap("SELECT * FROM request_event WHERE action = 'REQUEST_CHANGES'"))
                .containsEntry("from_status", "PENDING_APPROVAL")
                .containsEntry("to_status", "CHANGES_REQUESTED")
                .containsEntry("step_level", "NIVEL_2")
                .containsEntry("comment", VALID_COMMENT);
        assertThat(detail(requestId, ownerToken).get("allowedActions")).isEqualTo(List.of("EDIT", "SUBMIT", "CANCEL"));
        assertThat(detail(requestId, level1Token).get("allowedActions")).isEqualTo(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject", "request-changes"})
    void deveRetornar422CommentRequiredSemComentarioOuComMenosDe10Caracteres(String action) {
        long requestId = submitted(ownerToken, "100.00");

        for (String comment : new String[] {null, "", "   curto   "}) {
            Response response = decide(action, requestId, level1Token, 1, comment);

            response.assertProblem(422, "COMMENT_REQUIRED");
            assertThat(response.json().get("detail").toString())
                    .contains("o comentário é obrigatório e precisa ter pelo menos 10 caracteres");
        }
        assertThat(detail(requestId, ownerToken))
                .containsEntry("status", "PENDING_APPROVAL")
                .containsEntry("lockVersion", 1);
    }

    @Test
    void deveAprovarSemComentario() {
        long requestId = submitted(ownerToken, "100.00");
        defineBudget(competenceOf(requestId), "100.00");

        Response response = api.post("/requests/" + requestId + "/approve", level1Token, "{ \"lockVersion\": 1 }");

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json()).containsEntry("status", "APPROVED");
        assertThat(steps(response).get(0)).containsEntry("comment", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "request-changes"})
    void deveRetornar403ParaSolicitanteAntesDeChecarOLockVersion(String action) {
        long requestId = submitted(ownerToken, "100.00");

        decide(action, requestId, ownerToken, 99, null).assertProblem(403, "ACCESS_DENIED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "request-changes"})
    void deveRetornar404ForaDaVisibilidadeAntesDo403EDo409(String action) {
        long requestId = submitted(ownerToken, "100.00");
        String otherRequesterToken = token(
                saveUser("colega@example.com", Role.SOLICITANTE, null, costCenterId),
                Role.SOLICITANTE,
                null,
                costCenterId);

        decide(action, requestId, otherCostCenterApproverToken, 99, VALID_COMMENT)
                .assertProblem(404, "NOT_FOUND");
        decide(action, requestId, otherRequesterToken, 99, VALID_COMMENT).assertProblem(404, "NOT_FOUND");
        decide(action, 999_999L, adminToken, 0, VALID_COMMENT).assertProblem(404, "NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "request-changes"})
    void deveRetornar409QuandoLockVersionDivergeAntesDasRegrasDeNegocio(String action) {
        long requestId = submitted(ownerToken, "100.00");

        // Sem comentário e sem orçamento: só não dá 422 porque o 409 vem antes.
        decide(action, requestId, level1Token, 0, null).assertProblem(409, "CONCURRENT_MODIFICATION");

        assertThat(detail(requestId, ownerToken)).containsEntry("lockVersion", 1);
    }

    @Test
    void deveRetornar409AoRepetirAAprovacaoComOLockVersionAntigo() {
        long requestId = submitted(ownerToken, "5000.00");
        assertThat(approve(requestId, level1Token, 1, null).status()).isEqualTo(200);

        approve(requestId, level2Token, 1, null).assertProblem(409, "CONCURRENT_MODIFICATION");
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "CANCELLED", "CHANGES_REQUESTED"})
    void deveRetornar422InvalidTransitionForaDePendingApproval(String status) {
        long requestId = submitted(ownerToken, "100.00");
        jdbc.update("UPDATE purchase_request SET status = ? WHERE id = ?", status, requestId);

        Response response = approve(requestId, level1Token, 1, null);

        response.assertProblem(422, "INVALID_TRANSITION");
        assertThat(response.json().get("detail").toString())
                .startsWith("Não é possível aprovar a solicitação: ela está em " + status);
    }

    @Test
    void deveRetornar400QuandoLockVersionNaoEnviadoOuComentarioAcimaDoLimite() {
        long requestId = submitted(ownerToken, "100.00");

        Response missingLock = api.post("/requests/" + requestId + "/approve", level1Token, "{}");
        Response longComment = reject(requestId, level1Token, 1, "a".repeat(2001));

        missingLock.assertProblem(400, "VALIDATION_FAILED");
        assertThat(missingLock.errorFields()).containsExactly("lockVersion");
        longComment.assertProblem(400, "VALIDATION_FAILED");
        assertThat(longComment.errorFields()).containsExactly("comment");
    }

    /** Solicitação de um item submetida pelo dono do token (lockVersion 1). */
    private long submitted(String creatorToken, String unitPrice) {
        String body = """
                {
                  "title": "Compra para o time de TI",
                  "description": "Itens para a equipe",
                  "category": "EQUIPMENT",
                  "neededBy": "%s",
                  "items": [ { "description": "Item", "quantity": 1, "unitPrice": "%s" } ]
                }
                """.formatted(LocalDate.now(BusinessTime.ZONE).plusDays(30), unitPrice);
        Response created = api.post("/requests", creatorToken, body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        long requestId = ((Number) created.json().get("id")).longValue();
        Response submitted = api.post("/requests/" + requestId + "/submit", creatorToken, "{ \"lockVersion\": 0 }");
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        return requestId;
    }

    private YearMonth competenceOf(long requestId) {
        return YearMonth.parse((String) detail(requestId, adminToken).get("competenceMonth"));
    }

    private void defineBudget(YearMonth month, String amount) {
        jdbc.update(
                "INSERT INTO budget (cost_center_id, month, amount, consumed) VALUES (?, ?, ?, 0)",
                costCenterId,
                month.atDay(1),
                new BigDecimal(amount));
    }

    private BigDecimal consumed() {
        return jdbc
                .queryForList("SELECT consumed FROM budget WHERE cost_center_id = ?", BigDecimal.class, costCenterId)
                .stream()
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private long countEvents(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM request_event WHERE action = ?", Long.class, action);
    }

    private Response approve(long requestId, String token, long lockVersion, String comment) {
        return decide("approve", requestId, token, lockVersion, comment);
    }

    private Response reject(long requestId, String token, long lockVersion, String comment) {
        return decide("reject", requestId, token, lockVersion, comment);
    }

    private Response requestChanges(long requestId, String token, long lockVersion, String comment) {
        return decide("request-changes", requestId, token, lockVersion, comment);
    }

    private Response decide(String action, long requestId, String token, long lockVersion, String comment) {
        String body = comment == null
                ? "{ \"lockVersion\": " + lockVersion + " }"
                : "{ \"lockVersion\": " + lockVersion + ", \"comment\": \"" + comment + "\" }";
        return api.post("/requests/" + requestId + "/" + action, token, body);
    }

    private Map<String, Object> detail(long requestId, String token) {
        Response response = api.get("/requests/" + requestId, token);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Response response) {
        return steps(response.json());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> detail) {
        return (List<Map<String, Object>>) detail.get("steps");
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
