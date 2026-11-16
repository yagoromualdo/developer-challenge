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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PurchaseRequestSubmitCancelIntegrationTest {

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
    private Long ownerId;
    private Long adminId;
    private String ownerToken;
    private String otherRequesterToken;
    private String approverToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        Long costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
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
        adminId = saveUser("admin@example.com", Role.ADMIN, null, null);
        adminToken = token(adminId, Role.ADMIN, null, null);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @ParameterizedTest(name = "{0} de {1} -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "EQUIPMENT | 1000.00  | NIVEL_1",
                "EQUIPMENT | 1000.01  | NIVEL_1,NIVEL_2",
                "EQUIPMENT | 10000.00 | NIVEL_1,NIVEL_2",
                "EQUIPMENT | 10000.01 | NIVEL_1,NIVEL_2,ADMIN",
                "SOFTWARE  | 500.00   | NIVEL_1,ADMIN",
                "SOFTWARE  | 5000.00  | NIVEL_1,NIVEL_2,ADMIN",
                "SOFTWARE  | 25000.00 | NIVEL_1,NIVEL_2,ADMIN"
            })
    void deveMontarACadeiaPorValorECategoriaAoSubmeter(String category, String unitPrice, String expectedLevels) {
        long requestId = createDraft(category, unitPrice);

        Response response = api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(0));

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        List<String> levels = Arrays.asList(expectedLevels.split(","));
        assertThat(steps(response)).extracting(step -> step.get("level")).containsExactlyElementsOf(levels);
        assertThat(steps(response))
                .extracting(step -> step.get("order"))
                .containsExactlyElementsOf(orders(levels.size()));
        assertThat(steps(response))
                .allSatisfy(step ->
                        assertThat(step).containsEntry("status", "PENDING").containsEntry("decidedBy", null));
        assertThat(jdbc.queryForList(
                        "SELECT s.level FROM approval_step s JOIN request_version v ON v.id = s.version_id"
                                + " WHERE v.request_id = ? ORDER BY s.step_order",
                        String.class,
                        requestId))
                .containsExactlyElementsOf(levels);
    }

    @Test
    void deveSubmeterRascunhoGravandoSubmissaoECompetenciaNoFusoDeSaoPaulo() {
        long requestId = createDraft("EQUIPMENT", "4899.90");

        Response response = api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(0));

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        Map<String, Object> submitted = response.json();
        Instant submittedAt = Instant.parse((String) submitted.get("submittedAt"));
        assertThat(submitted)
                .containsEntry("status", "PENDING_APPROVAL")
                .containsEntry("version", 1)
                .containsEntry("lockVersion", 1)
                .containsEntry(
                        "competenceMonth",
                        BusinessTime.competenceOf(submittedAt).toString())
                .containsEntry("allowedActions", List.of("CANCEL"));
        assertThat(jdbc.queryForObject("SELECT competence_month FROM request_version", LocalDate.class))
                .isEqualTo(BusinessTime.competenceOf(submittedAt).atDay(1));
        assertThat(detail(requestId, approverToken).get("allowedActions"))
                .isEqualTo(List.of("APPROVE", "REJECT", "REQUEST_CHANGES"));
    }

    @Test
    void deveRegistrarEventoDeSubmissaoComOrigemDestinoEVersao() {
        long requestId = createDraft("EQUIPMENT", "100.00");

        Response response = api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(0));

        Map<String, Object> event = jdbc.queryForMap("SELECT * FROM request_event WHERE action = 'SUBMIT'");
        assertThat(event)
                .containsEntry("request_id", requestId)
                .containsEntry("version_number", 1)
                .containsEntry("actor_id", ownerId)
                .containsEntry("actor_role", "SOLICITANTE")
                .containsEntry("from_status", "DRAFT")
                .containsEntry("to_status", "PENDING_APPROVAL")
                .containsEntry("http_request_id", response.headers().getFirst(RequestId.HEADER));
    }

    @Test
    void deveRetornar409QuandoLockVersionDivergeNaSubmissaoENoCancelamento() {
        long requestId = createDraft("EQUIPMENT", "100.00");

        api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(7))
                .assertProblem(409, "CONCURRENT_MODIFICATION");
        api.post("/requests/" + requestId + "/cancel", ownerToken, lockVersionBody(7))
                .assertProblem(409, "CONCURRENT_MODIFICATION");

        assertThat(detail(requestId, ownerToken))
                .containsEntry("status", "DRAFT")
                .containsEntry("lockVersion", 0)
                .containsEntry("steps", List.of());
    }

    @Test
    void deveRetornar409NaSegundaSubmissaoComOLockVersionAntigo() {
        long requestId = createDraft("EQUIPMENT", "100.00");
        assertThat(api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(0))
                        .status())
                .isEqualTo(200);

        api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(0))
                .assertProblem(409, "CONCURRENT_MODIFICATION");
    }

    @Test
    void deveRetornar400QuandoLockVersionNaoEnviado() {
        long requestId = createDraft("EQUIPMENT", "100.00");

        Response submit = api.post("/requests/" + requestId + "/submit", ownerToken, "{}");
        Response cancel = api.post("/requests/" + requestId + "/cancel", ownerToken, "{ \"comment\": \"sem lock\" }");

        submit.assertProblem(400, "VALIDATION_FAILED");
        assertThat(submit.errorFields()).containsExactly("lockVersion");
        cancel.assertProblem(400, "VALIDATION_FAILED");
        assertThat(cancel.errorFields()).containsExactly("lockVersion");
    }

    @Test
    void deveRetornar404ParaRascunhoDeOutroUsuarioAntesDeChecarOLockVersion() {
        long requestId = createDraft("EQUIPMENT", "100.00");

        for (String token : List.of(otherRequesterToken, approverToken, adminToken)) {
            api.post("/requests/" + requestId + "/submit", token, lockVersionBody(7))
                    .assertProblem(404, "NOT_FOUND");
            api.post("/requests/" + requestId + "/cancel", token, lockVersionBody(7))
                    .assertProblem(404, "NOT_FOUND");
        }
    }

    @Test
    void deveRetornar422NotRequestOwnerQuandoNaoDonoQueVeASolicitacaoTentaSubmeter() {
        long requestId = submittedRequest();
        jdbc.update("UPDATE purchase_request SET status = 'CHANGES_REQUESTED' WHERE id = ?", requestId);

        for (String token : List.of(adminToken, approverToken)) {
            Response response = api.post("/requests/" + requestId + "/submit", token, lockVersionBody(1));

            response.assertProblem(422, "NOT_REQUEST_OWNER");
            assertThat(response.json().get("detail"))
                    .isEqualTo("Não é possível submeter a solicitação: somente o dono da solicitação pode fazer isso.");
        }
    }

    @Test
    void deveRetornar422AoSubmeterSolicitacaoJaPendente() {
        long requestId = submittedRequest();

        Response response = api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(1));

        response.assertProblem(422, "INVALID_TRANSITION");
        assertThat(response.json().get("detail").toString())
                .startsWith("Não é possível submeter a solicitação: ela está em PENDING_APPROVAL");
    }

    @Test
    void deveCriarNovaVersaoComCadeiaDoZeroAoReenviarSemEditar() {
        long requestId = submittedRequest();
        jdbc.update(
                "UPDATE approval_step SET status = 'CHANGES_REQUESTED', decided_by = ?, decided_at = now(),"
                        + " comment = 'Ajustar a quantidade'",
                adminId);
        jdbc.update("UPDATE purchase_request SET status = 'CHANGES_REQUESTED' WHERE id = ?", requestId);

        Response response = api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(1));

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("status", "PENDING_APPROVAL")
                .containsEntry("version", 2)
                .containsEntry("total", "100.00");
        assertThat(steps(response))
                .singleElement()
                .satisfies(step ->
                        assertThat(step).containsEntry("level", "NIVEL_1").containsEntry("status", "PENDING"));
        assertThat(jdbc.queryForList(
                        "SELECT s.status FROM approval_step s JOIN request_version v ON v.id = s.version_id"
                                + " WHERE v.request_id = ? AND v.version_number = 1",
                        String.class,
                        requestId))
                .containsExactly("CHANGES_REQUESTED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM request_version WHERE request_id = ? AND submitted_at IS NOT NULL",
                        Long.class,
                        requestId))
                .isEqualTo(2);
    }

    @Test
    void deveCancelarORascunhoPeloDonoRegistrandoOComentario() {
        long requestId = createDraft("EQUIPMENT", "100.00");

        Response response = api.post(
                "/requests/" + requestId + "/cancel",
                ownerToken,
                "{ \"lockVersion\": 0, \"comment\": \"  Compra adiada para o próximo ano  \" }");

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("status", "CANCELLED")
                .containsEntry("lockVersion", 1)
                .containsEntry("allowedActions", List.of());
        assertThat(jdbc.queryForMap("SELECT * FROM request_event WHERE action = 'CANCEL'"))
                .containsEntry("from_status", "DRAFT")
                .containsEntry("to_status", "CANCELLED")
                .containsEntry("actor_id", ownerId)
                .containsEntry("comment", "Compra adiada para o próximo ano");
    }

    @Test
    void devePermitirQueAdminCanceleSolicitacaoPendenteDeOutroUsuario() {
        long requestId = submittedRequest();

        Response response = api.post("/requests/" + requestId + "/cancel", adminToken, lockVersionBody(1));

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json()).containsEntry("status", "CANCELLED");
        assertThat(jdbc.queryForMap("SELECT * FROM request_event WHERE action = 'CANCEL'"))
                .containsEntry("actor_id", adminId)
                .containsEntry("actor_role", "ADMIN")
                .containsEntry("from_status", "PENDING_APPROVAL")
                .containsEntry("comment", null);
    }

    @Test
    void deveRetornar422NotRequestOwnerQuandoAprovadorTentaCancelar() {
        long requestId = submittedRequest();

        Response response = api.post("/requests/" + requestId + "/cancel", approverToken, lockVersionBody(1));

        response.assertProblem(422, "NOT_REQUEST_OWNER");
        assertThat(response.json().get("detail"))
                .isEqualTo("Não é possível cancelar a solicitação: somente o dono da solicitação ou um ADMIN pode"
                        + " cancelá-la.");
        assertThat(detail(requestId, ownerToken)).containsEntry("status", "PENDING_APPROVAL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "CANCELLED"})
    void deveRetornar422AoCancelarSolicitacaoEmEstadoFinal(String status) {
        long requestId = submittedRequest();
        jdbc.update("UPDATE purchase_request SET status = ? WHERE id = ?", status, requestId);

        Response response = api.post("/requests/" + requestId + "/cancel", ownerToken, lockVersionBody(1));

        response.assertProblem(422, "INVALID_TRANSITION");
        assertThat(response.json().get("detail"))
                .isEqualTo("Não é possível cancelar a solicitação: ela está em " + status
                        + ", um estado final que não aceita mais nenhuma ação.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM request_event WHERE action = 'CANCEL'", Long.class))
                .isZero();
    }

    @Test
    void deveRetornar409AntesDo422EmEstadoFinalComLockVersionDesatualizado() {
        long requestId = submittedRequest();
        jdbc.update("UPDATE purchase_request SET status = 'APPROVED' WHERE id = ?", requestId);

        api.post("/requests/" + requestId + "/cancel", ownerToken, lockVersionBody(0))
                .assertProblem(409, "CONCURRENT_MODIFICATION");
    }

    @Test
    void deveRetornar400ParaComentarioDeCancelamentoAcimaDoLimite() {
        long requestId = createDraft("EQUIPMENT", "100.00");
        String body = "{ \"lockVersion\": 0, \"comment\": \"%s\" }".formatted("a".repeat(2001));

        Response response = api.post("/requests/" + requestId + "/cancel", ownerToken, body);

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly("comment");
    }

    /** Rascunho de R$ 100,00 já submetido pelo dono (lockVersion 1, cadeia NIVEL_1). */
    private long submittedRequest() {
        long requestId = createDraft("EQUIPMENT", "100.00");
        Response response = api.post("/requests/" + requestId + "/submit", ownerToken, lockVersionBody(0));
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return requestId;
    }

    private long createDraft(String category, String unitPrice) {
        String body = """
                {
                  "title": "Compra para o time de TI",
                  "description": "Itens para a equipe",
                  "category": "%s",
                  "neededBy": "%s",
                  "items": [ { "description": "Item", "quantity": 1, "unitPrice": "%s" } ]
                }
                """.formatted(category, LocalDate.now(BusinessTime.ZONE).plusDays(30), unitPrice);
        Response response = api.post("/requests", ownerToken, body);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return ((Number) response.json().get("id")).longValue();
    }

    private Map<String, Object> detail(long requestId, String token) {
        Response response = api.get("/requests/" + requestId, token);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Response response) {
        return (List<Map<String, Object>>) response.json().get("steps");
    }

    private static List<Integer> orders(int size) {
        return IntStream.rangeClosed(1, size).boxed().toList();
    }

    private static String lockVersionBody(long lockVersion) {
        return "{ \"lockVersion\": " + lockVersion + " }";
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
