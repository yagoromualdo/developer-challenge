package com.kingspan.purchase.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.kingspan.purchase.shared.web.RequestId;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP dos testes de integração: devolve status e corpo sem lançar exceção em 4xx/5xx. */
public final class ApiClient {

    private final RestClient client;
    private final JsonMapper json;

    public ApiClient(int port, JsonMapper json) {
        this.client = RestClient.create("http://localhost:" + port);
        this.json = json;
    }

    public Response get(String path, String token) {
        return send(HttpMethod.GET, path, token, null);
    }

    public Response post(String path, String token, String body) {
        return send(HttpMethod.POST, path, token, body);
    }

    public Response put(String path, String token, String body) {
        return send(HttpMethod.PUT, path, token, body);
    }

    public Response patch(String path, String token, String body) {
        return send(HttpMethod.PATCH, path, token, body);
    }

    private Response send(HttpMethod method, String path, String token, String body) {
        RestClient.RequestBodySpec spec = client.method(method).uri(path).headers(headers -> {
            if (token != null) {
                headers.setBearerAuth(token);
            }
        });
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((request, response) -> new Response(
                response.getStatusCode().value(), response.getHeaders(), response.bodyTo(String.class), json));
    }

    public record Response(int status, HttpHeaders headers, String body, JsonMapper mapper) {

        @SuppressWarnings("unchecked")
        public Map<String, Object> json() {
            return body == null || body.isBlank() ? Map.of() : mapper.readValue(body, Map.class);
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> jsonList() {
            return mapper.readValue(body, List.class);
        }

        @SuppressWarnings("unchecked")
        public List<Object> errorFields() {
            return ((List<Map<String, Object>>) json().get("errors"))
                    .stream().map(error -> error.get("field")).toList();
        }

        public Response assertProblem(int expectedStatus, String code) {
            assertThat(status).as(body).isEqualTo(expectedStatus);
            assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(json())
                    .containsEntry("status", expectedStatus)
                    .containsEntry("code", code)
                    .containsEntry("requestId", headers.getFirst(RequestId.HEADER))
                    .containsKeys("type", "title", "detail", "instance");
            return this;
        }
    }
}
