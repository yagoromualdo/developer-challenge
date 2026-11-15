package com.kingspan.purchase.user.web;

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
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class UserAdminIntegrationTest {

    private static final String PASSWORD = "Senha-Forte-123";
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private CostCenterRepository costCenters;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JsonMapper json;

    private ApiClient api;
    private Long costCenterId;
    private Long otherCostCenterId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        api = new ApiClient(port, json);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        otherCostCenterId = costCenters
                .save(new CostCenterEntity("CC-OPS-01", "Operações", NOW))
                .getId();
        adminToken = tokenFor(saveUser("admin@example.com", Role.ADMIN, null, null));
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveRetornar403ParaNaoAdminEmTodosOsEndpointsDeUsuarios() {
        UserEntity requester = saveUser("maria@example.com", Role.SOLICITANTE, null, costCenterId);
        UserEntity approver = saveUser("joao@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, costCenterId);

        for (String token : List.of(tokenFor(requester), tokenFor(approver))) {
            api.get("/users", token).assertProblem(403, "ACCESS_DENIED");
            api.post("/users", token, createBody("novo@example.com", "ADMIN", null, null))
                    .assertProblem(403, "ACCESS_DENIED");
            api.patch("/users/" + requester.getId(), token, "{\"role\":\"ADMIN\"}")
                    .assertProblem(403, "ACCESS_DENIED");
        }
        assertThat(users.findById(requester.getId()).orElseThrow().getRole()).isEqualTo(Role.SOLICITANTE);
        assertThat(users.count()).isEqualTo(3);
    }

    @Test
    void deveRetornar401SemToken() {
        api.get("/users", null).assertProblem(401, "UNAUTHORIZED");
    }

    @Test
    void deveCriarAprovadorQueConsegueLogarComAsClaimsDoPapel() {
        Response created = api.post(
                "/users", adminToken, createBody("Aprovador@Example.com", "APROVADOR", "NIVEL_1", costCenterId));

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.json())
                .containsEntry("email", "aprovador@example.com")
                .containsEntry("role", "APROVADOR")
                .containsEntry("approverLevel", "NIVEL_1")
                .containsEntry("costCenterId", costCenterId.intValue())
                .doesNotContainKeys("password", "passwordHash");
        assertNoSecrets(created);

        Response login = api.post(
                "/auth/login", null, "{\"email\":\"aprovador@example.com\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(login.status()).isEqualTo(200);
        String accessToken = login.json().get("accessToken").toString();
        assertThat(jwtDecoder.decode(accessToken).getClaimAsString("approverLevel"))
                .isEqualTo("NIVEL_1");
    }

    @Test
    void deveCriarAdminGlobalSemCentroDeCusto() {
        Response created = api.post("/users", adminToken, createBody("admin2@example.com", "ADMIN", null, null));

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.json()).containsEntry("role", "ADMIN").containsEntry("costCenterId", null);
    }

    @Test
    void deveRetornar422QuandoPapelENivelSaoIncoerentes() {
        api.post("/users", adminToken, createBody("a@example.com", "APROVADOR", null, costCenterId))
                .assertProblem(422, "APPROVER_LEVEL_REQUIRED");
        api.post("/users", adminToken, createBody("b@example.com", "SOLICITANTE", "NIVEL_1", costCenterId))
                .assertProblem(422, "APPROVER_LEVEL_NOT_ALLOWED");
        api.post("/users", adminToken, createBody("c@example.com", "SOLICITANTE", null, null))
                .assertProblem(422, "COST_CENTER_REQUIRED");
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void deveRetornar422ParaCentroDeCustoInexistenteOuEmailRepetido() {
        api.post("/users", adminToken, createBody("novo@example.com", "SOLICITANTE", null, 999L))
                .assertProblem(422, "COST_CENTER_NOT_FOUND");
        api.post("/users", adminToken, createBody("ADMIN@example.com", "SOLICITANTE", null, costCenterId))
                .assertProblem(422, "EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void deveRetornar400ComErrosPorCampo() {
        Response response = api.post("/users", adminToken, "{\"name\":\"\",\"email\":\"x\",\"password\":\"curta\"}");

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly("email", "name", "password", "role");
    }

    @Test
    void deveRetornar400ParaPapelDesconhecido() {
        api.post("/users", adminToken, createBody("x@example.com", "CHEFE", null, costCenterId))
                .assertProblem(400, "MALFORMED_REQUEST");
    }

    @Test
    void deveListarUsuariosSemExporSenha() {
        saveUser("maria@example.com", Role.SOLICITANTE, null, costCenterId);

        Response response = api.get("/users", adminToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.jsonList())
                .extracting(user -> user.get("email"))
                .containsExactly("admin@example.com", "maria@example.com");
        assertNoSecrets(response);
    }

    @Test
    void devePromoverSolicitanteAAprovadorETrocarDeCentroDeCusto() {
        UserEntity requester = saveUser("maria@example.com", Role.SOLICITANTE, null, costCenterId);

        Response response = api.patch(
                "/users/" + requester.getId(),
                adminToken,
                "{\"role\":\"APROVADOR\",\"approverLevel\":\"NIVEL_2\",\"costCenterId\":" + otherCostCenterId + "}");

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("role", "APROVADOR")
                .containsEntry("approverLevel", "NIVEL_2")
                .containsEntry("costCenterId", otherCostCenterId.intValue())
                .containsEntry("email", "maria@example.com");
        UserEntity saved = users.findById(requester.getId()).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.APROVADOR);
        assertThat(saved.getUpdatedAt()).isAfter(NOW);
    }

    @Test
    void deveDescartarNivelAoRebaixarAprovadorParaSolicitante() {
        UserEntity approver = saveUser("joao@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1, costCenterId);

        Response response = api.patch("/users/" + approver.getId(), adminToken, "{\"role\":\"SOLICITANTE\"}");

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("role", "SOLICITANTE")
                .containsEntry("approverLevel", null)
                .containsEntry("costCenterId", costCenterId.intValue());
    }

    @Test
    void deveRetornar422AoEditarParaPerfilIncoerente() {
        UserEntity requester = saveUser("maria@example.com", Role.SOLICITANTE, null, costCenterId);

        api.patch("/users/" + requester.getId(), adminToken, "{\"approverLevel\":\"NIVEL_1\"}")
                .assertProblem(422, "APPROVER_LEVEL_NOT_ALLOWED");
        api.patch("/users/" + requester.getId(), adminToken, "{\"role\":\"APROVADOR\"}")
                .assertProblem(422, "APPROVER_LEVEL_REQUIRED");
        api.patch("/users/" + requester.getId(), adminToken, "{\"costCenterId\":999}")
                .assertProblem(422, "COST_CENTER_NOT_FOUND");
        UserEntity unchanged = users.findById(requester.getId()).orElseThrow();
        assertThat(unchanged.getRole()).isEqualTo(Role.SOLICITANTE);
        assertThat(unchanged.getCostCenterId()).isEqualTo(costCenterId);
    }

    @Test
    void deveRetornar404AoEditarUsuarioInexistente() {
        api.patch("/users/999", adminToken, "{\"role\":\"ADMIN\"}").assertProblem(404, "NOT_FOUND");
    }

    @Test
    void deveRetornar400ParaIdNaoNumerico() {
        Response response = api.patch("/users/abc", adminToken, "{\"role\":\"ADMIN\"}");

        response.assertProblem(400, "VALIDATION_FAILED");
        assertThat(response.errorFields()).containsExactly("id");
    }

    private UserEntity saveUser(String email, Role role, ApproverLevel level, Long costCenter) {
        return users.save(new UserEntity(
                "Usuário " + role, email, passwordEncoder.encode(PASSWORD), role, level, costCenter, NOW));
    }

    private String tokenFor(UserEntity user) {
        return jwtService
                .issue(new Actor(user.getId(), user.getRole(), user.getCostCenterId(), user.getApproverLevel()))
                .value();
    }

    private static String createBody(String email, String role, String level, Long costCenter) {
        return "{\"name\":\"Fulano\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"" + role
                + "\",\"approverLevel\":" + quoted(level) + ",\"costCenterId\":" + costCenter + "}";
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static void assertNoSecrets(Response response) {
        assertThat(response.body())
                .doesNotContain(PASSWORD)
                .doesNotContainIgnoringCase("passwordHash")
                .doesNotContain("{bcrypt}")
                .doesNotContain("$2a$");
    }
}
