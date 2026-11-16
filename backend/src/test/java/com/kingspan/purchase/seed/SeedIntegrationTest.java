package com.kingspan.purchase.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.shared.domain.BusinessTime;
import com.kingspan.purchase.support.ApiClient;
import com.kingspan.purchase.support.ApiClient.Response;
import com.kingspan.purchase.support.DatabaseCleaner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.seed.enabled=true")
@Import(TestcontainersConfiguration.class)
class SeedIntegrationTest {

    private static final List<String> TABLES = List.of(
            "cost_center",
            "app_user",
            "budget",
            "purchase_request",
            "request_version",
            "request_item",
            "approval_step",
            "request_event");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SeedRunner seedRunner;

    @Autowired
    private Clock clock;

    @Autowired
    private JsonMapper json;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        seedRunner.run(null);
    }

    @Test
    void deveCriarCentrosDeCustoEUsuariosExigidosPeloReadme() {
        assertThat(count("SELECT count(*) FROM cost_center")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM app_user WHERE role = 'ADMIN'")).isEqualTo(2);
        for (String code : List.of(DemoDataSeeder.IT_CODE, DemoDataSeeder.MAINTENANCE_CODE)) {
            List<String> profiles = jdbc.queryForList("""
                    SELECT u.role || coalesce(':' || u.approver_level, '')
                      FROM app_user u JOIN cost_center c ON c.id = u.cost_center_id
                     WHERE c.code = ?
                    """, String.class, code);
            assertThat(profiles).containsExactlyInAnyOrder("SOLICITANTE", "APROVADOR:NIVEL_1", "APROVADOR:NIVEL_2");
        }
    }

    @Test
    void deveCriarSolicitacoesEmTodosOsEstadosPelasRegrasReais() {
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM purchase_request", String.class))
                .containsExactlyInAnyOrder(
                        "DRAFT", "PENDING_APPROVAL", "CHANGES_REQUESTED", "APPROVED", "REJECTED", "CANCELLED");
        assertThat(count("SELECT count(*) FROM purchase_request WHERE current_version = 2"))
                .isEqualTo(1);
        // Cada transição passou pelo service: há evento de histórico, marcado como vindo do seed.
        assertThat(jdbc.queryForList("SELECT DISTINCT http_request_id FROM request_event", String.class))
                .containsExactly(SeedRunner.SEED_REQUEST_ID);
    }

    @Test
    void deveDeixarOOrcamentoDoMesCorrenteQuaseEsgotadoNaManutencao() {
        YearMonth currentMonth = BusinessTime.competenceOf(clock.instant());
        Map<String, Object> budget = jdbc.queryForMap("""
                SELECT b.month, b.amount, b.consumed
                  FROM budget b JOIN cost_center c ON c.id = b.cost_center_id
                 WHERE c.code = ?
                """, DemoDataSeeder.MAINTENANCE_CODE);

        assertThat(budget.get("month").toString())
                .isEqualTo(currentMonth.atDay(1).toString());
        assertThat((BigDecimal) budget.get("amount")).isEqualByComparingTo(DemoDataSeeder.MAINTENANCE_BUDGET);
        assertThat((BigDecimal) budget.get("consumed")).isEqualByComparingTo("7200.00");
    }

    @Test
    void naoDeveDuplicarDadosAoRodarDeNovo() {
        Map<String, Integer> before = countRows();

        seedRunner.run(null);

        assertThat(countRows()).isEqualTo(before);
    }

    @Test
    void devePermitirReproduzirBudgetExceededComOAprovadorNivel2DaManutencao() {
        String token = login("aprovador2.mnt@example.com");
        Map<String, Object> tools = api.get("/approvals/inbox", token).jsonList().stream()
                .filter(item -> item.get("title").equals("Kit de ferramentas para a oficina"))
                .findFirst()
                .orElseThrow();

        Response response = api.post(
                "/requests/" + tools.get("id") + "/approve",
                token,
                "{\"lockVersion\":" + tools.get("lockVersion") + "}");

        response.assertProblem(422, "BUDGET_EXCEEDED");
        assertThat(api.get("/requests/" + tools.get("id"), token).json()).containsEntry("status", "PENDING_APPROVAL");
    }

    private String login(String email) {
        Response response = api.post(
                "/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + DemoDataSeeder.PASSWORD + "\"}");
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return (String) response.json().get("accessToken");
    }

    private Map<String, Integer> countRows() {
        return TABLES.stream()
                .collect(Collectors.toMap(table -> table, table -> count("SELECT count(*) FROM " + table)));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
