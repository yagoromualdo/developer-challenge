package com.kingspan.purchase.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.web.RequestId;
import com.kingspan.purchase.support.DatabaseCleaner;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

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
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JsonMapper json;

    private RestClient client;
    private Long costCenterId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        client = RestClient.create("http://localhost:" + port);
        costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveDevolverJwtComAsClaimsDoUsuarioNoLoginValido() {
        UserEntity approver = saveUser("aprovador@example.com", Role.APROVADOR, ApproverLevel.NIVEL_2, costCenterId);

        Response response = post("/auth/login", loginBody("Aprovador@Example.com"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json()).containsEntry("tokenType", "Bearer").containsKeys("accessToken", "expiresAt");
        Jwt jwt = jwtDecoder.decode(response.json().get("accessToken").toString());
        assertThat(jwt.getSubject()).isEqualTo(approver.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("APROVADOR");
        assertThat(jwt.<Number>getClaim("costCenterId").longValue()).isEqualTo(costCenterId);
        assertThat(jwt.getClaimAsString("approverLevel")).isEqualTo("NIVEL_2");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(response.json().get("expiresAt"))
                .isEqualTo(jwt.getExpiresAt().toString());
        assertNoSecrets(response);
    }

    @Test
    void deveOmitirClaimsOpcionaisParaAdminGlobal() {
        saveUser("admin@example.com", Role.ADMIN, null, null);

        Response response = post("/auth/login", loginBody("admin@example.com"));

        Jwt jwt = jwtDecoder.decode(response.json().get("accessToken").toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.hasClaim("costCenterId")).isFalse();
        assertThat(jwt.hasClaim("approverLevel")).isFalse();
    }

    @Test
    void deveRetornar401ComMensagemUnicaParaSenhaErradaEUsuarioInexistente() {
        saveUser("solicitante@example.com", Role.SOLICITANTE, null, costCenterId);

        Response wrongPassword =
                post("/auth/login", "{\"email\":\"solicitante@example.com\",\"password\":\"senha-errada\"}");
        Response unknownUser = post("/auth/login", "{\"email\":\"ninguem@example.com\",\"password\":\"senha-errada\"}");

        assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS");
        assertProblem(unknownUser, 401, "INVALID_CREDENTIALS");
        assertThat(wrongPassword.json().get("detail"))
                .isEqualTo(unknownUser.json().get("detail"));
        assertThat(wrongPassword.json().get("title"))
                .isEqualTo(unknownUser.json().get("title"));
    }

    @Test
    void deveRetornar401QuandoSenhaExcedeLimiteDoBcrypt() {
        saveUser("solicitante@example.com", Role.SOLICITANTE, null, costCenterId);

        Response response =
                post("/auth/login", "{\"email\":\"solicitante@example.com\",\"password\":\"" + "a".repeat(100) + "\"}");

        assertProblem(response, 401, "INVALID_CREDENTIALS");
    }

    @Test
    void deveRetornar401ProblemJsonSemToken() {
        Response response = get("/auth/me", null);

        assertProblem(response, 401, "UNAUTHORIZED");
        assertThat(response.headers().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(response.json()).containsEntry("instance", "/auth/me");
    }

    @Test
    void deveRetornar401ProblemJsonComTokenInvalido() {
        assertProblem(get("/auth/me", "nao-e-um-jwt"), 401, "UNAUTHORIZED");
    }

    @Test
    void deveRetornar401ProblemJsonComTokenExpirado() {
        UserEntity user = saveUser("solicitante@example.com", Role.SOLICITANTE, null, costCenterId);
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                .claim("role", "SOLICITANTE")
                .claim("costCenterId", costCenterId)
                .build();
        String expired = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertProblem(get("/auth/me", expired), 401, "UNAUTHORIZED");
    }

    @Test
    void deveDevolverUsuarioLogadoEmMeSemSenhaNemHash() {
        UserEntity user = saveUser("aprovador@example.com", Role.APROVADOR, ApproverLevel.NIVEL_1, costCenterId);

        Response response = get("/auth/me", login("aprovador@example.com"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json())
                .containsEntry("id", user.getId().intValue())
                .containsEntry("email", "aprovador@example.com")
                .containsEntry("role", "APROVADOR")
                .containsEntry("approverLevel", "NIVEL_1")
                .containsEntry("costCenterId", costCenterId.intValue());
        assertNoSecrets(response);
    }

    @Test
    void deveCriarSomenteSolicitanteNoCadastroMesmoSePedirOutroPapel() {
        Response response = post(
                "/auth/register",
                "{\"name\":\"Maria\",\"email\":\"Maria@Example.com\",\"password\":\"" + PASSWORD
                        + "\",\"costCenterId\":" + costCenterId + ",\"role\":\"ADMIN\",\"approverLevel\":\"NIVEL_2\"}");

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json())
                .containsEntry("role", "SOLICITANTE")
                .containsEntry("approverLevel", null)
                .containsEntry("email", "maria@example.com");
        assertNoSecrets(response);
        UserEntity saved = users.findByEmail("maria@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.SOLICITANTE);
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(get("/auth/me", login("maria@example.com")).status()).isEqualTo(200);
    }

    @Test
    void deveRetornar422QuandoEmailJaCadastradoIgnorandoMaiusculas() {
        saveUser("maria@example.com", Role.SOLICITANTE, null, costCenterId);

        Response response = post("/auth/register", registerBody("MARIA@example.com", costCenterId));

        assertProblem(response, 422, "EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void deveRetornar422QuandoCentroDeCustoNaoExiste() {
        Response response = post("/auth/register", registerBody("maria@example.com", 9999L));

        assertProblem(response, 422, "COST_CENTER_NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveRetornar400NoCadastroInvalidoSemEcoarASenha() {
        String shortPassword = "curta12";
        Response response = post(
                "/auth/register",
                "{\"name\":\"\",\"email\":\"invalido\",\"password\":\"" + shortPassword + "\",\"costCenterId\":"
                        + costCenterId + "}");

        assertProblem(response, 400, "VALIDATION_FAILED");
        List<Map<String, Object>> errors =
                (List<Map<String, Object>>) response.json().get("errors");
        assertThat(errors).extracting(error -> error.get("field")).containsExactly("email", "name", "password");
        assertThat(response.body()).doesNotContain(shortPassword);
    }

    @Test
    void deveRetornar400QuandoSenhaExcedeLimiteDeBytesDoBcrypt() {
        // 40 caracteres, mas 80 bytes em UTF-8.
        Response response = post(
                "/auth/register",
                "{\"name\":\"Maria\",\"email\":\"maria@example.com\",\"password\":\"" + "é".repeat(40)
                        + "\",\"costCenterId\":" + costCenterId + "}");

        assertProblem(response, 400, "VALIDATION_FAILED");
    }

    @Test
    void deveRetornar403ProblemJsonQuandoPapelNaoPermite() {
        saveUser("solicitante@example.com", Role.SOLICITANTE, null, costCenterId);

        Response response = get("/users", login("solicitante@example.com"));

        assertProblem(response, 403, "ACCESS_DENIED");
    }

    @Test
    void naoDeveNegarRotaDeAdminParaAdmin() {
        saveUser("admin@example.com", Role.ADMIN, null, null);

        Response response = get("/users", login("admin@example.com"));

        assertThat(response.status()).isNotIn(401, 403);
    }

    private UserEntity saveUser(String email, Role role, ApproverLevel level, Long costCenter) {
        return users.save(new UserEntity(
                "Usuário " + role, email, passwordEncoder.encode(PASSWORD), role, level, costCenter, NOW));
    }

    private String login(String email) {
        Response response = post("/auth/login", loginBody(email));
        assertThat(response.status()).isEqualTo(200);
        return response.json().get("accessToken").toString();
    }

    private static String loginBody(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private static String registerBody(String email, Long costCenter) {
        return "{\"name\":\"Maria\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"costCenterId\":"
                + costCenter + "}";
    }

    private static void assertNoSecrets(Response response) {
        assertThat(response.body())
                .doesNotContain(PASSWORD)
                .doesNotContainIgnoringCase("passwordHash")
                .doesNotContain("{bcrypt}")
                .doesNotContain("$2a$");
    }

    private static void assertProblem(Response response, int status, String code) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.json())
                .containsEntry("status", status)
                .containsEntry("code", code)
                .containsEntry("requestId", response.headers().getFirst(RequestId.HEADER))
                .containsKeys("type", "title", "detail", "instance");
        assertNoSecrets(response);
    }

    private Response post(String path, String body) {
        return exchange(
                client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private Response get(String path, String token) {
        return exchange(client.get().uri(path).headers(headers -> {
            if (token != null) {
                headers.setBearerAuth(token);
            }
        }));
    }

    @SuppressWarnings("unchecked")
    private Response exchange(RestClient.RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> {
            String body = response.bodyTo(String.class);
            Map<String, Object> parsed = body == null || body.isBlank() ? Map.of() : json.readValue(body, Map.class);
            return new Response(response.getStatusCode().value(), response.getHeaders(), body, parsed);
        });
    }

    private record Response(int status, HttpHeaders headers, String body, Map<String, Object> json) {}
}
