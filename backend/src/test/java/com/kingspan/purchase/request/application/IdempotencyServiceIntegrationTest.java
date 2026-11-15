package com.kingspan.purchase.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kingspan.purchase.TestcontainersConfiguration;
import com.kingspan.purchase.costcenter.infra.CostCenterEntity;
import com.kingspan.purchase.costcenter.infra.CostCenterRepository;
import com.kingspan.purchase.request.domain.Category;
import com.kingspan.purchase.request.domain.Money;
import com.kingspan.purchase.request.domain.RequestItem;
import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.error.ConflictException;
import com.kingspan.purchase.shared.error.ErrorCode;
import com.kingspan.purchase.support.DatabaseCleaner;
import com.kingspan.purchase.user.infra.UserEntity;
import com.kingspan.purchase.user.infra.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Força o caminho da corrida de forma determinística: outra transação grava a mesma chave enquanto a operação roda,
 * e a UNIQUE(user_id, key) barra o INSERT desta.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotencyServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String KEY = "chave-disputada";
    private static final String WINNER_BODY = "{\"id\": 42}";

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private PurchaseRequestService requestService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CostCenterRepository costCenters;

    @Autowired
    private UserRepository users;

    private Actor owner;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        Long costCenterId = costCenters
                .save(new CostCenterEntity("CC-TI-01", "Tecnologia", NOW))
                .getId();
        Long userId = users.save(new UserEntity(
                        "Usuário", "dono@example.com", "{bcrypt}hash", Role.SOLICITANTE, null, costCenterId, NOW))
                .getId();
        owner = new Actor(userId, Role.SOLICITANTE, costCenterId, null);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void deveDesfazerACriacaoEDevolverARespostaVencedoraQuandoAUniqueBarraOInsert() {
        Map<String, String> payload = Map.of("title", "Notebooks");

        IdempotentResponse result = idempotency.execute(owner.userId(), KEY, payload, () -> {
            requestService.create(owner, null, draft());
            saveKeyInAnotherTransaction(idempotency.hash(payload));
            return IdempotentResponse.fresh(201, "{\"id\": 1}");
        });

        assertThat(result.replayed()).isTrue();
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body()).isEqualTo(WINNER_BODY);
        assertThat(count("purchase_request")).isZero();
        assertThat(count("request_event")).isZero();
        assertThat(count("idempotency_key")).isEqualTo(1);
    }

    @Test
    void deveRetornar409QuandoAChaveVencedoraTemOutroPayload() {
        assertThatThrownBy(() -> idempotency.execute(owner.userId(), KEY, Map.of("title", "Notebooks"), () -> {
                    saveKeyInAnotherTransaction(idempotency.hash(Map.of("title", "Monitores")));
                    return IdempotentResponse.fresh(201, "{\"id\": 1}");
                }))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    // Outra thread usa outra conexão do pool: o INSERT é commitado na hora, fora da transação da operação.
    private void saveKeyInAnotherTransaction(String requestHash) {
        CompletableFuture.runAsync(() -> jdbc.update(
                        "INSERT INTO idempotency_key (user_id, key, request_hash, response_status, response_body,"
                                + " created_at) VALUES (?, ?, ?, 201, ?::jsonb, now())",
                        owner.userId(),
                        KEY,
                        requestHash,
                        WINNER_BODY))
                .orTimeout(10, TimeUnit.SECONDS)
                .join();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static RequestDraft draft() {
        return new RequestDraft(
                "Notebooks",
                "Substituição de equipamentos",
                Category.EQUIPMENT,
                LocalDate.now().plusDays(30),
                List.of(new RequestItem("Notebook", 1, Money.of("4899.90"))));
    }
}
