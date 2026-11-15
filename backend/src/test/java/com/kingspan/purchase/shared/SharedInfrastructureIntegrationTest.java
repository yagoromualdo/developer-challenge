package com.kingspan.purchase.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.kingspan.purchase.shared.security.JwtService;
import com.kingspan.purchase.shared.web.RequestId;
import com.kingspan.purchase.shared.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, SharedInfrastructureIntegrationTest.ErrorProbeController.class})
class SharedInfrastructureIntegrationTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @LocalServerPort
    private int port;

    @Autowired
    private ListableBeanFactory beanFactory;

    private RestClient client;

    @Autowired
    private JwtService jwtService;

    // Token válido em todas as chamadas: os probes e a rota inexistente exigem autenticação desde a E11.
    @BeforeEach
    void setUp() {
        String token =
                jwtService.issue(new Actor(1L, Role.SOLICITANTE, 1L, null)).value();
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    @Test
    void deveGerarRequestIdQuandoClienteNaoEnvia() {
        Response response = get("/health", headers -> {});

        assertThat(response.headers().getFirst(RequestId.HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void deveDevolverRequestIdRecebidoDoCliente() {
        Response response = get("/health", headers -> headers.set(RequestId.HEADER, "abc-123"));

        assertThat(response.headers().getFirst(RequestId.HEADER)).isEqualTo("abc-123");
    }

    @Test
    void deveSubstituirRequestIdRecebidoQuandoInseguro() {
        Response response = get("/health", headers -> headers.set(RequestId.HEADER, "a".repeat(200)));

        assertThat(response.headers().getFirst(RequestId.HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void deveRegistrarRequestIdFilterAntesDeQualquerOutroFiltro() {
        // Mesmo algoritmo de ordenação que o Spring Boot usa ao registrar os filtros no container.
        AbstractFilterRegistrationBean<?> first = new ServletContextInitializerBeans(beanFactory)
                .stream()
                        .filter(AbstractFilterRegistrationBean.class::isInstance)
                        .map(AbstractFilterRegistrationBean.class::cast)
                        .findFirst()
                        .orElseThrow();

        assertThat(first.getFilter()).isInstanceOf(RequestIdFilter.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveResponderHealthUpComBancoNaRaiz() {
        Response response = get("/health", headers -> {});

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.body()).containsEntry("status", "UP");
        Map<String, Object> components = (Map<String, Object>) response.body().get("components");
        assertThat((Map<String, Object>) components.get("db")).containsEntry("status", "UP");
    }

    @Test
    void naoDeveExporOutrosEndpointsDoActuator() {
        assertThat(get("/actuator/health", headers -> {}).status().value()).isEqualTo(404);
        assertThat(get("/info", headers -> {}).status().value()).isEqualTo(404);
        assertThat(get("/env", headers -> {}).status().value()).isEqualTo(404);
    }

    @Test
    void deveAbrirSwaggerUiEApiDocs() {
        assertThat(get("/swagger-ui/index.html", headers -> {}).status().value())
                .isEqualTo(200);
        assertThat(get("/v3/api-docs", headers -> {}).status().value()).isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveRetornar400ComErrorsPorCampo() {
        Response response = exchange(client.post()
                .uri("/test-probe/validation")
                .header(RequestId.HEADER, "req-400")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"title\":\"\",\"items\":[{\"quantity\":0}]}"));

        assertProblem(response, 400, "VALIDATION_FAILED", "req-400");
        List<Map<String, Object>> errors =
                (List<Map<String, Object>>) response.body().get("errors");
        assertThat(errors).extracting(error -> error.get("field")).containsExactly("items[0].quantity", "title");
    }

    @Test
    void deveRetornar400QuandoJsonMalformado() {
        Response response = exchange(client.post()
                .uri("/test-probe/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{nao-e-json"));

        assertProblem(response, 400, "MALFORMED_REQUEST", response.headers().getFirst(RequestId.HEADER));
    }

    @Test
    void deveRetornar422ComCodigoDaRegraDeNegocio() {
        Response response = get("/test-probe/business", headers -> headers.set(RequestId.HEADER, "req-422"));

        assertProblem(response, 422, "BUDGET_EXCEEDED", "req-422");
        assertThat(response.body()).containsEntry("detail", "Saldo insuficiente: disponível 10.00, exigido 20.00.");
        assertThat(response.body()).containsEntry("type", "urn:problem-type:budget-exceeded");
    }

    @Test
    void deveRetornar409EmConflitoDeLockOtimista() {
        Response response = get("/test-probe/conflict", headers -> {});

        assertProblem(
                response, 409, "CONCURRENT_MODIFICATION", response.headers().getFirst(RequestId.HEADER));
    }

    @Test
    void deveRetornar500SemVazarDetalhesInternos() {
        Response response = get("/test-probe/unexpected", headers -> {});

        assertProblem(response, 500, "INTERNAL_ERROR", response.headers().getFirst(RequestId.HEADER));
        assertThat(response.body().get("detail").toString()).doesNotContain("segredo");
    }

    @Test
    void deveRetornar404EmProblemDetails() {
        Response response = get("/rota-inexistente", headers -> {});

        assertProblem(response, 404, "NOT_FOUND", response.headers().getFirst(RequestId.HEADER));
    }

    private static void assertProblem(Response response, int status, String code, String requestId) {
        assertThat(response.status().value()).isEqualTo(status);
        assertThat(response.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(requestId).isNotBlank();
        assertThat(response.body())
                .containsEntry("status", status)
                .containsEntry("code", code)
                .containsEntry("requestId", requestId)
                .containsKeys("type", "title", "detail", "instance");
    }

    private Response get(String path, Consumer<HttpHeaders> headers) {
        return exchange(client.get().uri(path).headers(headers));
    }

    @SuppressWarnings("unchecked")
    private static Response exchange(RestClient.RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> {
            MediaType contentType = response.getHeaders().getContentType();
            boolean json = contentType != null && contentType.getSubtype().endsWith("json");
            Map<String, Object> body = json ? response.bodyTo(Map.class) : null;
            return new Response(response.getStatusCode(), response.getHeaders(), body);
        });
    }

    private record Response(HttpStatusCode status, HttpHeaders headers, Map<String, Object> body) {}

    @RestController
    static class ErrorProbeController {

        record ProbeItem(@Positive int quantity) {}

        record ProbePayload(
                @NotBlank String title, @NotEmpty List<@Valid ProbeItem> items) {}

        @PostMapping("/test-probe/validation")
        void validation(@Valid @RequestBody ProbePayload payload) {}

        @GetMapping("/test-probe/business")
        void business() {
            throw new BusinessRuleException(
                    ErrorCode.BUDGET_EXCEEDED, "Saldo insuficiente: disponível 10.00, exigido 20.00.");
        }

        @GetMapping("/test-probe/conflict")
        void conflict() {
            throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
        }

        @GetMapping("/test-probe/unexpected")
        void unexpected() {
            throw new IllegalStateException("detalhe interno segredo");
        }
    }
}
