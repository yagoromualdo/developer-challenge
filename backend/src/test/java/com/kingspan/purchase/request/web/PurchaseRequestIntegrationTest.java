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
import com.kingspan.purchase.shared.web.RequestId;
import com.kingspan.purchase.support.ApiClient;
import com.kingspan.purchase.support.ApiClient.Response;
import com.kingspan.purchase.support.DatabaseCleaner;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class PurchaseRequestIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

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
    private Long otherCostCenterId;
    private Long ownerId;
    private String ownerToken;
    private String otherRequesterToken;
    private String approverToken;
    private String otherCostCenterApproverToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        otherCostCenterId = costCenters
                .save(new CostCenterEntity("CC-RH-01", "Recursos Humanos", NOW))
                .getId();
        ownerId = saveUser("dono@example.com", Role.SOLICITANTE, null, costCenterId);
        ownerToken = token(ownerId, Role.SOLICITANTE, null, costCenterId);
        otherRequesterToken = token(
                saveUser("colega@example.com", Role.SOLICITANTE, null, costCenterId),
                Role.SOLICITANTE,
                null,
                costCenterId);
        approverToken = token(
                saveUser("aprovador@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, costCenterId),
                Role.APROVADOR,
                ApproverLevel.NIVEL_2,
                costCenterId);
        otherCostCenterApproverToken = token(
                saveUser("aprovador.rh@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, otherCostCenterId),
                Role.APROVADOR,
                ApproverLevel.NIVEL_2,
                otherCostCenterId);
        adminToken = token(saveUser("admin@example.com", Role.ADMIN, null, null), Role.ADMIN, null, null);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveCriarRascunhoComTotalCalculadoNoBackIgnorandoOValorDoCliente() {
        Response response = api.post("/requests", ownerToken, body(null, "\"total\": \"1.00\","));

        assertThat(response.status()).as(response.body()).isEqualTo(201);
        Map<String, Object> created = response.json();
        assertThat(response.headers().getLocation()).hasToString("/requests/" + created.get("id"));
        assertThat(created)
                .containsEntry("status", "DRAFT")
                .containsEntry("ownerId", ownerId.intValue())
                .containsEntry("costCenterId", costCenterId.intValue())
                .containsEntry("version", 1)
                .containsEntry("lockVersion", 0)
                .containsEntry("total", "24679.40")
                .containsEntry("neededBy", neededBy().toString())
                .containsEntry("steps", List.of())
                .containsEntry("allowedActions", List.of("EDIT", "SUBMIT", "CANCEL"));
        assertThat(created.get("items"))
                .isEqualTo(List.of(
                        Map.of(
                                "description",
                                "Notebook 16GB/512GB",
                                "quantity",
                                5,
                                "unitPrice",
                                "4899.90",
                                "subtotal",
                                "24499.50"),
                        Map.of("description", "Mouse", "quantity", 2, "unitPrice", "89.95", "subtotal", "179.90")));
        assertThat(jdbc.queryForObject("SELECT total FROM request_version", BigDecimal.class))
                .isEqualByComparingTo("24679.40");
    }

    @Test
    void deveRegistrarEventoDeCriacaoComORequestIdDaRequisicao() {
        Response response = api.post("/requests", ownerToken, body(null, ""));

        Map<String, Object> event = jdbc.queryForMap("SELECT * FROM request_event");
        assertThat(event)
                .containsEntry("request_id", id(response))
                .containsEntry("version_number", 1)
                .containsEntry("actor_id", ownerId)
                .containsEntry("actor_role", "SOLICITANTE")
                .containsEntry("action", "CREATE")
                .containsEntry("from_status", null)
                .containsEntry("to_status", "DRAFT")
                .containsEntry("http_request_id", response.headers().getFirst(RequestId.HEADER));
    }

    @Test
    void deveRetornar400ComErrosPorCampo() {
        String invalid = """
                {
                  "title": " ",
                  "description": "Substituição de equipamentos",
                  "neededBy": "%s",
                  "items": [
                    { "description": "Notebook", "quantity": 0, "unitPrice": "10.505" },
                    { "description": "", "quantity": 1, "unitPrice": "0" }
                  ]
                }
                """.formatted(LocalDate.now(BusinessTime.ZONE));

        Response response = api.post("/requests", ownerToken, invalid);

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields())
                .containsExactly(
                        "category",
                        "items[0].quantity",
                        "items[0].unitPrice",
                        "items[1].description",
                        "items[1].unitPrice",
                        "neededBy",
                        "title");
        assertThat(count("purchase_request")).isZero();
    }

    @Test
    void deveRetornar400ParaSolicitacaoSemItensOuComTituloAcimaDoLimiteDaColuna() {
        String body = """
                { "title": "%s", "description": "Descrição", "category": "OTHER", "items": [] }
                """.formatted("a".repeat(201));

        Response response = api.post("/requests", ownerToken, body);

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly("items", "title");
    }

    @Test
    void deveRetornar422QuandoNaoAdminCriaEmOutroCentroDeCusto() {
        api.post("/requests", ownerToken, body(otherCostCenterId, "")).assertProblem(422, "COST_CENTER_NOT_ALLOWED");
        api.post("/requests", approverToken, body(otherCostCenterId, "")).assertProblem(422, "COST_CENTER_NOT_ALLOWED");
        assertThat(count("purchase_request")).isZero();
    }

    @Test
    void devePermitirQueAdminEscolhaOCentroDeCusto() {
        api.post("/requests", adminToken, body(null, "")).assertProblem(422, "COST_CENTER_REQUIRED");
        api.post("/requests", adminToken, body(999L, "")).assertProblem(422, "COST_CENTER_NOT_FOUND");

        Response created = api.post("/requests", adminToken, body(otherCostCenterId, ""));

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.json()).containsEntry("costCenterId", otherCostCenterId.intValue());
    }

    @Test
    void deveRetornar404ParaRascunhoDeOutroUsuarioMesmoParaAprovadorEAdmin() {
        long requestId = createDraft();

        for (String token : List.of(otherRequesterToken, approverToken, adminToken)) {
            api.get("/requests/" + requestId, token).assertProblem(404, "NOT_FOUND");
            api.put("/requests/" + requestId, token, editBody(0)).assertProblem(404, "NOT_FOUND");
        }
        assertThat(api.get("/requests/" + requestId, ownerToken).status()).isEqualTo(200);
    }

    @Test
    void deveRetornar404ComAMesmaMensagemParaSolicitacaoInexistente() {
        long requestId = createDraft();

        long missingId = requestId + 1;
        Response invisible = api.get("/requests/" + requestId, otherRequesterToken);
        Response missing = api.get("/requests/" + missingId, otherRequesterToken);

        assertThat(invisible.assertProblem(404, "NOT_FOUND").json())
                .containsEntry("detail", "Solicitação " + requestId + " não encontrada.");
        assertThat(missing.assertProblem(404, "NOT_FOUND").json())
                .containsEntry("detail", "Solicitação " + missingId + " não encontrada.");
    }

    @Test
    void deveAplicarAVisibilidadePorPapelForaDoRascunho() {
        long requestId = createDraft();
        returnForChanges(requestId);

        assertThat(detail(requestId, ownerToken)).containsEntry("allowedActions", List.of("EDIT", "SUBMIT", "CANCEL"));
        assertThat(detail(requestId, approverToken)).containsEntry("allowedActions", List.of());
        assertThat(detail(requestId, adminToken)).containsEntry("allowedActions", List.of("CANCEL"));
        api.get("/requests/" + requestId, otherRequesterToken).assertProblem(404, "NOT_FOUND");
        api.get("/requests/" + requestId, otherCostCenterApproverToken).assertProblem(404, "NOT_FOUND");
    }

    @Test
    void deveEditarRascunhoRecalculandoOTotalEIncrementandoOLockVersion() {
        long requestId = createDraft();

        Response edited = api.put("/requests/" + requestId, ownerToken, editBody(0));

        assertThat(edited.status()).as(edited.body()).isEqualTo(200);
        assertThat(edited.json())
                .containsEntry("status", "DRAFT")
                .containsEntry("version", 1)
                .containsEntry("lockVersion", 1)
                .containsEntry("title", "Monitores para o time de TI")
                .containsEntry("category", "SUPPLIES")
                .containsEntry("total", "3000.00")
                .containsEntry(
                        "items",
                        List.of(Map.of(
                                "description",
                                "Monitor 27\"",
                                "quantity",
                                3,
                                "unitPrice",
                                "1000.00",
                                "subtotal",
                                "3000.00")));
        assertThat(detail(requestId, ownerToken))
                .containsEntry("lockVersion", 1)
                .containsEntry("total", "3000.00");
        assertThat(count("request_item")).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM request_event WHERE action = 'EDIT'"))
                .containsEntry("from_status", "DRAFT")
                .containsEntry("to_status", "DRAFT")
                .containsEntry("version_number", 1)
                .containsEntry("http_request_id", edited.headers().getFirst(RequestId.HEADER));
    }

    @Test
    void deveRetornar409QuandoLockVersionDiverge() {
        long requestId = createDraft();
        assertThat(api.put("/requests/" + requestId, ownerToken, editBody(0)).status())
                .isEqualTo(200);

        api.put("/requests/" + requestId, ownerToken, editBody(0)).assertProblem(409, "CONCURRENT_MODIFICATION");
        api.put("/requests/" + requestId, ownerToken, editBody(7)).assertProblem(409, "CONCURRENT_MODIFICATION");
        assertThat(count("request_event")).isEqualTo(2);
    }

    @Test
    void deveRetornar400QuandoLockVersionNaoEnviadoNaEdicao() {
        long requestId = createDraft();

        Response response = api.put("/requests/" + requestId, ownerToken, body(null, ""));

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly("lockVersion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_APPROVAL", "APPROVED", "REJECTED", "CANCELLED"})
    void deveRetornar422AoEditarForaDeDraftOuChangesRequested(String status) {
        long requestId = createDraft();
        jdbc.update("UPDATE purchase_request SET status = ? WHERE id = ?", status, requestId);

        Response response = api.put("/requests/" + requestId, ownerToken, editBody(0));

        response.assertProblem(422, "INVALID_TRANSITION");
        assertThat(response.json().get("detail").toString())
                .startsWith("Não é possível editar a solicitação: ela está em " + status);
    }

    @Test
    void deveRetornar422NotRequestOwnerQuandoNaoDonoQueVeASolicitacaoTentaEditar() {
        long requestId = createDraft();
        returnForChanges(requestId);

        for (String token : List.of(adminToken, approverToken)) {
            Response response = api.put("/requests/" + requestId, token, editBody(0));

            response.assertProblem(422, "NOT_REQUEST_OWNER");
            assertThat(response.json().get("detail"))
                    .isEqualTo("Não é possível editar a solicitação: somente o dono da solicitação pode fazer isso.");
        }
        assertThat(count("request_version")).isEqualTo(1);
    }

    @Test
    void deveCriarVersaoDeTrabalhoAoEditarEmChangesRequestedPreservandoAVersaoSubmetida() {
        long requestId = createDraft();
        returnForChanges(requestId);

        Response first = api.put("/requests/" + requestId, ownerToken, editBody(0));
        Response second = api.put("/requests/" + requestId, ownerToken, editBody(1));

        assertThat(first.json()).containsEntry("version", 2).containsEntry("status", "CHANGES_REQUESTED");
        assertThat(second.status()).as(second.body()).isEqualTo(200);
        assertThat(second.json()).containsEntry("version", 2).containsEntry("lockVersion", 2);
        assertThat(jdbc.queryForList(
                        "SELECT title FROM request_version WHERE request_id = ? ORDER BY version_number",
                        String.class,
                        requestId))
                .containsExactly("Notebooks para o time de TI", "Monitores para o time de TI");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM request_item i JOIN request_version v ON v.id = i.version_id"
                                + " WHERE v.version_number = 1",
                        Long.class))
                .isEqualTo(2);
    }

    @Test
    void deveRetornar401SemToken() {
        api.post("/requests", null, body(null, "")).assertProblem(401, "UNAUTHORIZED");
        api.get("/requests/1", null).assertProblem(401, "UNAUTHORIZED");
    }

    private long createDraft() {
        Response response = api.post("/requests", ownerToken, body(null, ""));
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return id(response);
    }

    /** Simula a devolução (E17): a v1 fica submetida e a solicitação, em CHANGES_REQUESTED. */
    private void returnForChanges(long requestId) {
        jdbc.update("UPDATE purchase_request SET status = 'CHANGES_REQUESTED' WHERE id = ?", requestId);
        jdbc.update(
                "UPDATE request_version SET submitted_at = now(), competence_month = DATE '2026-11-01'"
                        + " WHERE request_id = ?",
                requestId);
    }

    private Map<String, Object> detail(long requestId, String token) {
        Response response = api.get("/requests/" + requestId, token);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json();
    }

    private static long id(Response response) {
        return ((Number) response.json().get("id")).longValue();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static String body(Long costCenterId, String extraFields) {
        return """
                {
                  %s
                  %s
                  "title": "Notebooks para o time de TI",
                  "description": "Substituição de 5 equipamentos com mais de 5 anos",
                  "category": "EQUIPMENT",
                  "neededBy": "%s",
                  "items": [
                    { "description": "Notebook 16GB/512GB", "quantity": 5, "unitPrice": "4899.90" },
                    { "description": "Mouse", "quantity": 2, "unitPrice": 89.95 }
                  ]
                }
                """.formatted(
                        costCenterId == null ? "" : "\"costCenterId\": " + costCenterId + ",", extraFields, neededBy());
    }

    private static String editBody(long lockVersion) {
        return """
                {
                  "title": "Monitores para o time de TI",
                  "description": "Troca dos monitores da equipe",
                  "category": "SUPPLIES",
                  "total": "1.00",
                  "items": [ { "description": "Monitor 27\\"", "quantity": 3, "unitPrice": "1000" } ],
                  "lockVersion": %d
                }
                """.formatted(lockVersion);
    }

    private static LocalDate neededBy() {
        return LocalDate.now(BusinessTime.ZONE).plusDays(30);
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
