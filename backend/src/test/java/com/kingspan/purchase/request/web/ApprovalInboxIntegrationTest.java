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
import java.time.Instant;
import java.time.LocalDate;
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
class ApprovalInboxIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String VALID_COMMENT = "Faltou o orçamento do fornecedor";

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
    private String ownerToken;
    private String level1Token;
    private String level2Token;
    private String adminToken;
    private String otherOwnerToken;
    private String otherCostCenterApproverToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        Long costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        Long otherCostCenterId = costCenters
                .save(new CostCenterEntity("CC-RH-01", "Recursos Humanos", NOW))
                .getId();
        ownerToken = user("dono@example.com", Role.SOLICITANTE, null, costCenterId);
        level1Token = user("nivel1@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1, costCenterId);
        level2Token = user("nivel2@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, costCenterId);
        adminToken = user("admin@example.com", Role.ADMIN, null, null);
        otherOwnerToken = user("dono.rh@example.com", Role.SOLICITANTE, null, otherCostCenterId);
        otherCostCenterApproverToken =
                user("nivel2.rh@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, otherCostCenterId);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveListarSoAsSolicitacoesDoCentroDeCustoDoAprovador() {
        long ti = submitted(ownerToken, "500.00");
        long rh = submitted(otherOwnerToken, "500.00");

        assertThat(inboxIds(level1Token)).containsExactly(ti);
        assertThat(inboxIds(otherCostCenterApproverToken)).containsExactly(rh);
        assertThat(inboxIds(adminToken)).containsExactly(ti, rh);
    }

    @Test
    void deveRespeitarONivelDaEtapaAtual() {
        long requestId = submitted(ownerToken, "5000.00");
        assertThat(inboxIds(level1Token)).containsExactly(requestId);
        assertThat(inboxIds(level2Token)).containsExactly(requestId);

        assertThat(approve(requestId, level1Token, 1).status()).isEqualTo(200);

        assertThat(inboxIds(level1Token)).isEmpty();
        assertThat(inboxIds(level2Token)).containsExactly(requestId);
        assertThat(inboxIds(adminToken)).containsExactly(requestId);
    }

    @Test
    void deveExcluirQuemJaAprovouOutraEtapaDaMesmaVersao() {
        long requestId = submitted(ownerToken, "5000.00");

        assertThat(approve(requestId, level2Token, 1).status()).isEqualTo(200);

        assertThat(inboxIds(level2Token)).isEmpty();
        assertThat(inboxIds(adminToken)).containsExactly(requestId);
    }

    @Test
    void deveExcluirASolicitacaoDoProprioAprovador() {
        long ownedByApprover = submitted(level1Token, "500.00");

        assertThat(inboxIds(level1Token)).isEmpty();
        assertThat(inboxIds(level2Token)).containsExactly(ownedByApprover);
    }

    @Test
    void deveListarSoSolicitacoesPendentes() {
        long pending = submitted(ownerToken, "500.00");
        created(ownerToken, "500.00");
        long changesRequested = submitted(ownerToken, "500.00");
        assertThat(requestChanges(changesRequested, level1Token, 1).status()).isEqualTo(200);
        long cancelled = submitted(ownerToken, "500.00");
        assertThat(api.post("/requests/" + cancelled + "/cancel", ownerToken, "{ \"lockVersion\": 1 }")
                        .status())
                .isEqualTo(200);

        assertThat(inboxIds(level1Token)).containsExactly(pending);
        assertThat(inboxIds(adminToken)).containsExactly(pending);
    }

    @Test
    void deveRetornarListaVaziaParaQuemNaoPodeAprovar() {
        submitted(ownerToken, "500.00");

        Response response = api.get("/approvals/inbox", ownerToken);

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.jsonList()).isEmpty();
    }

    @Test
    void deveRetornar401SemToken() {
        api.get("/approvals/inbox", null).assertProblem(401, "UNAUTHORIZED");
    }

    @Test
    void deveTrazerEtapaAtualELockVersionOrdenandoPelaSubmissaoMaisAntiga() {
        long first = submitted(ownerToken, "12000.00");
        long second = submitted(ownerToken, "500.00");
        assertThat(approve(first, level1Token, 1).status()).isEqualTo(200);

        List<Map<String, Object>> items = inbox(adminToken);

        assertThat(items)
                .extracting(item -> ((Number) item.get("id")).longValue())
                .containsExactly(first, second);
        assertThat(items.get(0))
                .containsEntry("status", "PENDING_APPROVAL")
                .containsEntry("version", 1)
                .containsEntry("lockVersion", 2)
                .containsEntry("currentStepLevel", "NIVEL_2")
                .containsEntry("total", "12000.00")
                .containsEntry("category", "EQUIPMENT")
                .containsKeys("title", "ownerId", "costCenterId", "neededBy", "submittedAt", "createdAt", "updatedAt");
        assertThat(items.get(1)).containsEntry("currentStepLevel", "NIVEL_1").containsEntry("lockVersion", 1);
    }

    @Test
    void deveCoincidirComOAllowedActionsDoDetalhe() {
        long nivel1 = submitted(ownerToken, "5000.00");
        long nivel2 = submitted(ownerToken, "5000.00");
        assertThat(approve(nivel2, level2Token, 1).status()).isEqualTo(200);
        long ownedByApprover = submitted(level1Token, "500.00");
        long otherCostCenter = submitted(otherOwnerToken, "500.00");
        List<Long> all = List.of(nivel1, nivel2, ownedByApprover, otherCostCenter);

        for (String token : List.of(level1Token, level2Token, adminToken, otherCostCenterApproverToken)) {
            List<Long> approvableInDetail = all.stream()
                    .filter(id -> allowedActions(id, token).contains("APPROVE"))
                    .toList();
            assertThat(inboxIds(token)).containsExactlyInAnyOrderElementsOf(approvableInDetail);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> allowedActions(long requestId, String token) {
        Response response = api.get("/requests/" + requestId, token);
        if (response.status() == 404) {
            return List.of();
        }
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return (List<Object>) response.json().get("allowedActions");
    }

    private List<Long> inboxIds(String token) {
        return inbox(token).stream()
                .map(item -> ((Number) item.get("id")).longValue())
                .toList();
    }

    private List<Map<String, Object>> inbox(String token) {
        Response response = api.get("/approvals/inbox", token);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.jsonList();
    }

    private long created(String creatorToken, String unitPrice) {
        String body = """
                {
                  "title": "Compra para o time",
                  "description": "Itens para a equipe",
                  "category": "EQUIPMENT",
                  "neededBy": "%s",
                  "items": [ { "description": "Item", "quantity": 1, "unitPrice": "%s" } ]
                }
                """.formatted(LocalDate.now(BusinessTime.ZONE).plusDays(30), unitPrice);
        Response created = api.post("/requests", creatorToken, body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        return ((Number) created.json().get("id")).longValue();
    }

    private long submitted(String creatorToken, String unitPrice) {
        long requestId = created(creatorToken, unitPrice);
        Response submitted = api.post("/requests/" + requestId + "/submit", creatorToken, "{ \"lockVersion\": 0 }");
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
        return requestId;
    }

    private Response approve(long requestId, String token, long lockVersion) {
        return api.post("/requests/" + requestId + "/approve", token, "{ \"lockVersion\": " + lockVersion + " }");
    }

    private Response requestChanges(long requestId, String token, long lockVersion) {
        return api.post(
                "/requests/" + requestId + "/request-changes",
                token,
                "{ \"lockVersion\": " + lockVersion + ", \"comment\": \"" + VALID_COMMENT + "\" }");
    }

    private String user(String email, Role role, ApproverLevel level, Long userCostCenterId) {
        Long userId = users.save(new UserEntity("Usuário", email, "{bcrypt}hash", role, level, userCostCenterId, NOW))
                .getId();
        return jwtService
                .issue(new Actor(userId, role, userCostCenterId, level))
                .value();
    }
}
