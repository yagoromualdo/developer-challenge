package com.kingspan.purchase.dashboard.web;

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
class DashboardIntegrationTest {

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
    private Long tiId;
    private Long rhId;
    private String ownerToken;
    private String colleagueToken;
    private String level1Token;
    private String adminToken;
    private String rhOwnerToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        tiId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        rhId = costCenters
                .save(new CostCenterEntity("CC-RH-01", "Recursos Humanos", NOW))
                .getId();
        ownerToken = user("dono@example.com", Role.SOLICITANTE, null, tiId);
        colleagueToken = user("colega@example.com", Role.SOLICITANTE, null, tiId);
        level1Token = user("nivel1@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1, tiId);
        adminToken = user("admin@example.com", Role.ADMIN, null, null);
        rhOwnerToken = user("dono.rh@example.com", Role.SOLICITANTE, null, rhId);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveContarSoAsPropriasSolicitacoesParaOSolicitante() {
        givenRequestsInBothCostCenters();

        Map<String, Object> summary = summary(ownerToken);

        assertThat(byStatus(summary))
                .containsEntry("DRAFT", 1)
                .containsEntry("PENDING_APPROVAL", 1)
                .containsEntry("APPROVED", 0);
        assertThat(summary).containsEntry("totalRequests", 2);
    }

    @Test
    void deveContarAsDoCentroDeCustoSemRascunhosAlheiosParaOAprovador() {
        givenRequestsInBothCostCenters();
        created(level1Token, "100.00");

        Map<String, Object> summary = summary(level1Token);

        // Pendentes do dono e do colega no CC-TI; dos rascunhos, só o próprio.
        assertThat(byStatus(summary)).containsEntry("PENDING_APPROVAL", 2).containsEntry("DRAFT", 1);
        assertThat(summary).containsEntry("totalRequests", 3);
    }

    @Test
    void deveContarTodosOsCentrosDeCustoSemRascunhosAlheiosParaOAdmin() {
        givenRequestsInBothCostCenters();

        Map<String, Object> summary = summary(adminToken);

        assertThat(byStatus(summary)).containsEntry("PENDING_APPROVAL", 3).containsEntry("DRAFT", 0);
        assertThat(summary).containsEntry("totalRequests", 3);
    }

    @Test
    void deveTrazerTodosOsStatusNaOrdemDoEnumMesmoSemSolicitacoes() {
        Map<String, Object> summary = summary(ownerToken);

        assertThat(byStatus(summary))
                .containsOnlyKeys("DRAFT", "PENDING_APPROVAL", "CHANGES_REQUESTED", "APPROVED", "REJECTED", "CANCELLED")
                .allSatisfy((status, count) -> assertThat(count).isEqualTo(0));
        assertThat(byStatus(summary).keySet())
                .containsExactly("DRAFT", "PENDING_APPROVAL", "CHANGES_REQUESTED", "APPROVED", "REJECTED", "CANCELLED");
        assertThat(summary).containsEntry("totalRequests", 0);
    }

    @Test
    void deveTrazerOrcamentoDoMesCorrenteSoDoProprioCentroDeCustoParaSolicitanteEAprovador() {
        YearMonth month = currentMonth();
        defineBudget(tiId, month, "10000.00");
        long approved = submitted(ownerToken, "500.00");
        assertThat(approve(approved, level1Token).status()).isEqualTo(200);

        for (String token : List.of(ownerToken, level1Token)) {
            Map<String, Object> summary = summary(token);
            assertThat(summary).containsEntry("month", month.toString());
            assertThat(budgets(summary))
                    .singleElement()
                    .satisfies(budget -> assertThat(budget)
                            .containsEntry("costCenterId", tiId.intValue())
                            .containsEntry("costCenterCode", "CC-TI-01")
                            .containsEntry("costCenterName", "Tecnologia")
                            .containsEntry("amount", "10000.00")
                            .containsEntry("consumed", "500.00")
                            .containsEntry("available", "9500.00"));
        }
        assertThat(budgets(summary(rhOwnerToken)))
                .extracting(budget -> budget.get("costCenterCode"))
                .containsExactly("CC-RH-01");
    }

    @Test
    void deveTrazerTodosOsCentrosDeCustoParaOAdminComZeroQuandoNaoHaOrcamentoNoMes() {
        YearMonth month = currentMonth();
        defineBudget(tiId, month, "10000.00");
        // Orçamento de outro mês não entra no dashboard: o CC-RH continua sem orçamento no mês corrente.
        defineBudget(rhId, month.plusMonths(1), "8000.00");

        List<Map<String, Object>> budgets = budgets(summary(adminToken));

        assertThat(budgets).extracting(budget -> budget.get("costCenterCode")).containsExactly("CC-RH-01", "CC-TI-01");
        assertThat(budgets.get(0))
                .containsEntry("amount", "0.00")
                .containsEntry("consumed", "0.00")
                .containsEntry("available", "0.00");
        assertThat(budgets.get(1)).containsEntry("amount", "10000.00").containsEntry("available", "10000.00");
    }

    @Test
    void deveRetornar401SemToken() {
        api.get("/dashboard/summary", null).assertProblem(401, "UNAUTHORIZED");
    }

    // TI: rascunho e pendente do dono, rascunho e pendente do colega; RH: um pendente.
    private void givenRequestsInBothCostCenters() {
        created(ownerToken, "100.00");
        submitted(ownerToken, "100.00");
        created(colleagueToken, "100.00");
        submitted(colleagueToken, "100.00");
        submitted(rhOwnerToken, "100.00");
    }

    private static YearMonth currentMonth() {
        return YearMonth.now(BusinessTime.ZONE);
    }

    private Map<String, Object> summary(String token) {
        Response response = api.get("/dashboard/summary", token);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> byStatus(Map<String, Object> summary) {
        return (Map<String, Object>) summary.get("requestsByStatus");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> budgets(Map<String, Object> summary) {
        return (List<Map<String, Object>>) summary.get("budgets");
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

    private Response approve(long requestId, String token) {
        return api.post("/requests/" + requestId + "/approve", token, "{ \"lockVersion\": 1 }");
    }

    private void defineBudget(Long costCenterId, YearMonth month, String amount) {
        jdbc.update(
                "INSERT INTO budget (cost_center_id, month, amount, consumed) VALUES (?, ?, ?, 0)",
                costCenterId,
                month.atDay(1),
                new BigDecimal(amount));
    }

    private String user(String email, Role role, ApproverLevel level, Long userCostCenterId) {
        Long userId = users.save(new UserEntity("Usuário", email, "{bcrypt}hash", role, level, userCostCenterId, NOW))
                .getId();
        return jwtService
                .issue(new Actor(userId, role, userCostCenterId, level))
                .value();
    }
}
