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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

/**
 * Cenário fixo: centro A com o dono, um colega e um aprovador; centro B com um solicitante e um aprovador. Cada
 * solicitação tem título único, usado nas asserções.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PurchaseRequestListIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String OWNER_DRAFT = "Notebooks do dono";
    private static final String OWNER_PENDING = "Licenças do dono";
    private static final String COLLEAGUE_APPROVED = "café do colega";
    private static final String COLLEAGUE_DRAFT = "Cadeiras do colega";
    private static final String APPROVER_DRAFT = "Monitores do aprovador";
    private static final String OTHER_CENTER_PENDING = "Consultoria do centro B";

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
    private String colleagueToken;
    private String approverToken;
    private String otherCenterRequesterToken;
    private String otherCenterApproverToken;
    private String adminToken;
    private long ownerPendingId;

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
        colleagueToken = userToken("colega@example.com", Role.SOLICITANTE, null, costCenterId);
        approverToken = userToken("aprovador@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1, costCenterId);
        otherCenterRequesterToken = userToken("rh@example.com", Role.SOLICITANTE, null, otherCostCenterId);
        otherCenterApproverToken =
                userToken("aprovador.rh@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, otherCostCenterId);
        adminToken = userToken("admin@example.com", Role.ADMIN, null, null);

        long ownerDraft = create(ownerToken, OWNER_DRAFT, "EQUIPMENT", "5000.00");
        ownerPendingId = create(ownerToken, OWNER_PENDING, "SOFTWARE", "300.00");
        long colleagueApproved = create(colleagueToken, COLLEAGUE_APPROVED, "SUPPLIES", "50.00");
        long colleagueDraft = create(colleagueToken, COLLEAGUE_DRAFT, "SUPPLIES", "800.00");
        long approverDraft = create(approverToken, APPROVER_DRAFT, "EQUIPMENT", "1200.00");
        long otherCenterPending = create(otherCenterRequesterToken, OTHER_CENTER_PENDING, "SERVICES", "2000.00");

        setStatus(ownerPendingId, "PENDING_APPROVAL");
        setStatus(colleagueApproved, "APPROVED");
        setStatus(otherCenterPending, "PENDING_APPROVAL");
        setCreatedAt(ownerDraft, "2026-11-01T12:00:00Z");
        setCreatedAt(ownerPendingId, "2026-11-02T12:00:00Z");
        setCreatedAt(colleagueApproved, "2026-11-03T12:00:00Z");
        setCreatedAt(colleagueDraft, "2026-11-04T12:00:00Z");
        setCreatedAt(approverDraft, "2026-11-05T12:00:00Z");
        setCreatedAt(otherCenterPending, "2026-11-06T12:00:00Z");
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveResponderNoFormatoDePaginacaoDoReadmeComOrdenacaoPadraoPorCriacaoDecrescente() {
        Response response = list(adminToken, "");

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        Map<String, Object> page = response.json();
        assertThat(page.keySet()).containsExactly("data", "page", "size", "totalElements", "totalPages");
        assertThat(page)
                .containsEntry("page", 0)
                .containsEntry("size", 20)
                .containsEntry("totalElements", 3)
                .containsEntry("totalPages", 1);
        assertThat(titles(response)).containsExactly(OTHER_CENTER_PENDING, COLLEAGUE_APPROVED, OWNER_PENDING);
    }

    @Test
    void deveDevolverItemResumidoComDadosDaVersaoAtualETotalComoString() {
        Map<String, Object> item =
                data(list(ownerToken, "status=PENDING_APPROVAL")).getFirst();

        assertThat(item.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "ownerId",
                        "costCenterId",
                        "status",
                        "version",
                        "title",
                        "category",
                        "total",
                        "neededBy",
                        "submittedAt",
                        "createdAt",
                        "updatedAt");
        assertThat(item)
                .containsEntry("id", (int) ownerPendingId)
                .containsEntry("ownerId", ownerId.intValue())
                .containsEntry("costCenterId", costCenterId.intValue())
                .containsEntry("status", "PENDING_APPROVAL")
                .containsEntry("version", 1)
                .containsEntry("title", OWNER_PENDING)
                .containsEntry("category", "SOFTWARE")
                .containsEntry("total", "300.00")
                .containsEntry("neededBy", neededBy().toString())
                .containsEntry("createdAt", "2026-11-02T12:00:00Z");
    }

    @Test
    void solicitanteDeveVerSomenteAsPropriasIncluindoRascunhos() {
        assertThat(titles(list(ownerToken, ""))).containsExactlyInAnyOrder(OWNER_DRAFT, OWNER_PENDING);
        assertThat(titles(list(colleagueToken, ""))).containsExactlyInAnyOrder(COLLEAGUE_APPROVED, COLLEAGUE_DRAFT);
        assertThat(titles(list(otherCenterRequesterToken, ""))).containsExactly(OTHER_CENTER_PENDING);
    }

    @Test
    void aprovadorDeveVerAsDoSeuCentroDeCustoMenosRascunhosDeOutros() {
        assertThat(titles(list(approverToken, "")))
                .containsExactlyInAnyOrder(OWNER_PENDING, COLLEAGUE_APPROVED, APPROVER_DRAFT);
        assertThat(titles(list(otherCenterApproverToken, ""))).containsExactly(OTHER_CENTER_PENDING);
    }

    @Test
    void adminDeveVerTodasMenosRascunhosDeOutros() {
        assertThat(titles(list(adminToken, "")))
                .containsExactlyInAnyOrder(OWNER_PENDING, COLLEAGUE_APPROVED, OTHER_CENTER_PENDING);
    }

    @Test
    void filtroPorDraftDeveTrazerSoRascunhosDoProprioUsuario() {
        assertThat(titles(list(approverToken, "status=DRAFT"))).containsExactly(APPROVER_DRAFT);
        assertThat(titles(list(adminToken, "status=DRAFT"))).isEmpty();
    }

    @Test
    void deveFiltrarPorUmOuVariosStatus() {
        assertThat(titles(list(adminToken, "status=PENDING_APPROVAL")))
                .containsExactlyInAnyOrder(OWNER_PENDING, OTHER_CENTER_PENDING);
        assertThat(titles(list(adminToken, "status=PENDING_APPROVAL,APPROVED")))
                .containsExactlyInAnyOrder(OWNER_PENDING, COLLEAGUE_APPROVED, OTHER_CENTER_PENDING);
        assertThat(titles(list(adminToken, "status=APPROVED&status=PENDING_APPROVAL")))
                .containsExactlyInAnyOrder(OWNER_PENDING, COLLEAGUE_APPROVED, OTHER_CENTER_PENDING);
    }

    @Test
    void deveFiltrarPorCategoriaDaVersaoAtual() {
        assertThat(titles(list(adminToken, "category=SOFTWARE"))).containsExactly(OWNER_PENDING);
        assertThat(titles(list(adminToken, "category=SOFTWARE,SUPPLIES&sort=total,desc")))
                .containsExactly(OWNER_PENDING, COLLEAGUE_APPROVED);
    }

    @Test
    void filtroPorCentroDeCustoDeveRespeitarAVisibilidade() {
        assertThat(titles(list(adminToken, "costCenterId=" + otherCostCenterId)))
                .containsExactly(OTHER_CENTER_PENDING);
        assertThat(titles(list(adminToken, "costCenterId=" + costCenterId)))
                .containsExactlyInAnyOrder(OWNER_PENDING, COLLEAGUE_APPROVED);
        assertThat(titles(list(ownerToken, "costCenterId=" + otherCostCenterId)))
                .isEmpty();
    }

    @Test
    void deveFiltrarPorPeriodoDeCriacaoComDiasInclusivosNoFusoDeSaoPaulo() {
        assertThat(titles(list(adminToken, "createdFrom=2026-11-02&createdTo=2026-11-03")))
                .containsExactlyInAnyOrder(OWNER_PENDING, COLLEAGUE_APPROVED);

        // 02:59:59Z ainda é dia 10 em São Paulo (UTC-3); 03:00:00Z já é dia 11.
        setCreatedAt(ownerPendingId, "2026-11-11T02:59:59Z");
        assertThat(titles(list(adminToken, "createdTo=2026-11-10"))).contains(OWNER_PENDING);
        setCreatedAt(ownerPendingId, "2026-11-11T03:00:00Z");
        assertThat(titles(list(adminToken, "createdTo=2026-11-10"))).doesNotContain(OWNER_PENDING);
        assertThat(titles(list(adminToken, "createdFrom=2026-11-11"))).containsExactly(OWNER_PENDING);
    }

    @Test
    void deveOrdenarPorCamposDaWhitelist() {
        assertThat(titles(list(adminToken, "sort=total,asc")))
                .containsExactly(COLLEAGUE_APPROVED, OWNER_PENDING, OTHER_CENTER_PENDING);
        assertThat(titles(list(adminToken, "sort=createdAt")))
                .containsExactly(OWNER_PENDING, COLLEAGUE_APPROVED, OTHER_CENTER_PENDING);
        assertThat(titles(list(approverToken, "sort=title,asc")))
                .containsExactly(COLLEAGUE_APPROVED, OWNER_PENDING, APPROVER_DRAFT);
        assertThat(titles(list(adminToken, "sort=status,DESC")))
                .containsExactly(OTHER_CENTER_PENDING, OWNER_PENDING, COLLEAGUE_APPROVED);
    }

    @Test
    void devePaginarNoServidor() {
        Response response = list(adminToken, "page=1&size=2&sort=total,asc");

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("page", 1)
                .containsEntry("size", 2)
                .containsEntry("totalElements", 3)
                .containsEntry("totalPages", 2);
        assertThat(titles(response)).containsExactly(OTHER_CENTER_PENDING);
        assertThat(titles(list(adminToken, "page=5&size=2"))).isEmpty();
    }

    @Test
    void deveListarCadaSolicitacaoUmaVezComOsDadosDaVersaoAtual() {
        jdbc.update("UPDATE purchase_request SET status = 'CHANGES_REQUESTED' WHERE id = ?", ownerPendingId);
        jdbc.update(
                "UPDATE request_version SET submitted_at = now(), competence_month = DATE '2026-11-01'"
                        + " WHERE request_id = ?",
                ownerPendingId);
        Response edited = api.put(
                "/requests/" + ownerPendingId,
                ownerToken,
                body("Licenças revisadas", "SOFTWARE", "9000.00")
                        .replace("\"items\"", "\"lockVersion\": 0, \"items\""));
        assertThat(edited.status()).as(edited.body()).isEqualTo(200);

        List<Map<String, Object>> data = data(list(ownerToken, "sort=total,desc&category=SOFTWARE"));

        assertThat(data).hasSize(1);
        assertThat(data.getFirst())
                .containsEntry("title", "Licenças revisadas")
                .containsEntry("version", 2)
                .containsEntry("total", "9000.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ownerId", "foo,desc", "createdAt,sideways", "createdAt,desc,id", ",desc"})
    void deveRetornar400ParaOrdenacaoForaDaWhitelistOuMalformada(String sort) {
        Response response = list(adminToken, "sort=" + sort);

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly("sort");
    }

    @Test
    void deveExplicarOsCamposPermitidosQuandoOCampoDeOrdenacaoEhDesconhecido() {
        Response response = list(adminToken, "sort=ownerId,desc");

        assertThat(response.json().get("errors"))
                .isEqualTo(List.of(Map.of(
                        "field",
                        "sort",
                        "message",
                        "campo de ordenação não permitido: 'ownerId'. Use um destes: createdAt, updatedAt, status,"
                                + " total, title")));
    }

    @ParameterizedTest
    @CsvSource({
        "page=-1, page",
        "size=0, size",
        "size=101, size",
        "status=FOO, status",
        "category=BOOKS, category",
        "costCenterId=abc, costCenterId",
        "createdFrom=2026-13-01, createdFrom",
        "createdFrom=2026-11-10&createdTo=2026-11-09, createdTo"
    })
    void deveRetornar400ParaParametrosInvalidos(String query, String field) {
        Response response = list(adminToken, query);

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly(field);
    }

    @Test
    void deveRetornar401SemToken() {
        api.get("/requests", null).assertProblem(401, "UNAUTHORIZED");
    }

    private Response list(String token, String query) {
        return api.get(query.isEmpty() ? "/requests" : "/requests?" + query, token);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> data(Response response) {
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return (List<Map<String, Object>>) response.json().get("data");
    }

    private static List<Object> titles(Response response) {
        return data(response).stream().map(item -> item.get("title")).toList();
    }

    private long create(String token, String title, String category, String unitPrice) {
        Response response = api.post("/requests", token, body(title, category, unitPrice));
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return ((Number) response.json().get("id")).longValue();
    }

    private static String body(String title, String category, String unitPrice) {
        return """
                {
                  "title": "%s",
                  "description": "Compra para o time",
                  "category": "%s",
                  "neededBy": "%s",
                  "items": [ { "description": "Item", "quantity": 1, "unitPrice": "%s" } ]
                }
                """.formatted(title, category, neededBy(), unitPrice);
    }

    private void setStatus(long requestId, String status) {
        jdbc.update("UPDATE purchase_request SET status = ? WHERE id = ?", status, requestId);
    }

    private void setCreatedAt(long requestId, String instant) {
        jdbc.update(
                "UPDATE purchase_request SET created_at = ? WHERE id = ?",
                Instant.parse(instant).atOffset(ZoneOffset.UTC),
                requestId);
    }

    private static LocalDate neededBy() {
        return LocalDate.now(BusinessTime.ZONE).plusDays(30);
    }

    private Long saveUser(String email, Role role, ApproverLevel level, Long userCostCenterId) {
        return users.save(new UserEntity("Usuário", email, "{bcrypt}hash", role, level, userCostCenterId, NOW))
                .getId();
    }

    private String userToken(String email, Role role, ApproverLevel level, Long userCostCenterId) {
        return token(saveUser(email, role, level, userCostCenterId), role, level, userCostCenterId);
    }

    private String token(Long userId, Role role, ApproverLevel level, Long userCostCenterId) {
        return jwtService
                .issue(new Actor(userId, role, userCostCenterId, level))
                .value();
    }
}
