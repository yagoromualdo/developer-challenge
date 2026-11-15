package com.kingspan.purchase.costcenter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.security.JwtService;
import com.kingspan.purchase.support.ApiClient;
import com.kingspan.purchase.support.ApiClient.Response;
import com.kingspan.purchase.support.DatabaseCleaner;
import java.time.Instant;
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
class CostCenterIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CostCenterRepository costCenters;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JsonMapper json;

    private ApiClient api;
    private Long costCenterId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        // As claims não são conferidas no banco (ADR 007), então estes endpoints não precisam do usuário gravado.
        adminToken = token(new Actor(1L, Role.ADMIN, null, null));
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveListarCentrosDeCustoParaQualquerUsuarioAutenticado() {
        costCenters.save(new CostCenterEntity("CC-ADM-01", "Administrativo", NOW));

        Response response = api.get("/cost-centers", token(new Actor(2L, Role.SOLICITANTE, costCenterId, null)));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.jsonList()).extracting(cc -> cc.get("code")).containsExactly("CC-ADM-01", "CC-TI-01");
        assertThat(response.jsonList().get(1))
                .containsEntry("id", costCenterId.intValue())
                .containsEntry("name", "Tecnologia")
                .containsEntry("createdAt", NOW.toString());
    }

    @Test
    void deveRetornar401SemToken() {
        api.get("/cost-centers", null).assertProblem(401, "UNAUTHORIZED");
    }

    @Test
    void deveRetornar403QuandoNaoAdminCriaCentroDeCusto() {
        String approver = token(new Actor(2L, Role.APROVADOR, costCenterId, ApproverLevel.NIVEL_2));
        String requester = token(new Actor(3L, Role.SOLICITANTE, costCenterId, null));

        api.post("/cost-centers", approver, "{\"code\":\"CC-NOVO\",\"name\":\"Novo\"}")
                .assertProblem(403, "ACCESS_DENIED");
        api.post("/cost-centers", requester, "{\"code\":\"CC-NOVO\",\"name\":\"Novo\"}")
                .assertProblem(403, "ACCESS_DENIED");
        assertThat(costCenters.count()).isEqualTo(1);
    }

    @Test
    void deveCriarCentroDeCustoComCodigoEmMaiusculas() {
        Response response = api.post("/cost-centers", adminToken, "{\"code\":\"cc-mkt_01\",\"name\":\"  Marketing \"}");

        assertThat(response.status()).as(response.body()).isEqualTo(201);
        assertThat(response.json())
                .containsEntry("code", "CC-MKT_01")
                .containsEntry("name", "Marketing")
                .containsKeys("id", "createdAt");
        assertThat(costCenters.existsByCode("CC-MKT_01")).isTrue();
    }

    @Test
    void deveRetornar422ParaCodigoJaUsadoIgnorandoMaiusculas() {
        api.post("/cost-centers", adminToken, "{\"code\":\"cc-ti-01\",\"name\":\"Outro\"}")
                .assertProblem(422, "COST_CENTER_CODE_ALREADY_EXISTS");
        assertThat(costCenters.count()).isEqualTo(1);
    }

    @Test
    void deveRetornar400ParaCamposInvalidos() {
        Response response =
                api.post("/cost-centers", adminToken, "{\"code\":\"CC TI\",\"name\":\"" + "x".repeat(121) + "\"}");

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly("code", "name");
    }

    private String token(Actor actor) {
        return jwtService.issue(actor).value();
    }
}
