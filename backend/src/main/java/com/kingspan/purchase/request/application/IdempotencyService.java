package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.infra.IdempotencyKeyEntity;
import com.kingspan.purchase.request.infra.IdempotencyKeyRepository;
import com.kingspan.purchase.shared.error.ConflictException;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Executa uma operação no máximo uma vez por (usuário, {@code Idempotency-Key}) dentro da validade (ADR 006). A
 * operação e a chave gravam na mesma transação: se a operação falhar, nada fica salvo e o cliente pode repetir.
 */
@Service
public class IdempotencyService {

    static final Duration TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository keys;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final Clock clock;

    public IdempotencyService(
            IdempotencyKeyRepository keys,
            PlatformTransactionManager transactionManager,
            JsonMapper json,
            Clock clock) {
        this.keys = keys;
        this.transaction = new TransactionTemplate(transactionManager);
        this.json = json;
        this.clock = clock;
    }

    /**
     * {@code payload} deve estar normalizado: dois corpos equivalentes precisam gerar o mesmo hash. Mesma chave com
     * outro payload → 409.
     */
    public IdempotentResponse execute(Long userId, String key, Object payload, Supplier<IdempotentResponse> operation) {
        String requestHash = hash(payload);
        try {
            return transaction.execute(status -> {
                Instant now = clock.instant();
                keys.deleteExpired(userId, key, now.minus(TTL));
                Optional<IdempotencyKeyEntity> saved = keys.findByUserIdAndIdempotencyKey(userId, key);
                if (saved.isPresent()) {
                    return replay(saved.get(), requestHash);
                }
                IdempotentResponse response = operation.get();
                keys.saveAndFlush(
                        new IdempotencyKeyEntity(userId, key, requestHash, response.status(), response.body(), now));
                return response;
            });
        } catch (DataIntegrityViolationException ex) {
            // Corrida: outra requisição com a mesma chave commitou primeiro e a UNIQUE(user_id, key) barrou esta;
            // a transação daqui já foi desfeita (inclusive a solicitação criada), então devolvemos a resposta salva.
            IdempotencyKeyEntity winner = findSaved(userId, key).orElseThrow(() -> ex);
            log.info(
                    "Idempotency-Key repetida em requisições simultâneas do usuário {}; devolvendo a resposta salva",
                    userId);
            return replay(winner, requestHash);
        }
    }

    private Optional<IdempotencyKeyEntity> findSaved(Long userId, String key) {
        TransactionTemplate readOnly = new TransactionTemplate(transaction.getTransactionManager());
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> keys.findByUserIdAndIdempotencyKey(userId, key));
    }

    private static IdempotentResponse replay(IdempotencyKeyEntity saved, String requestHash) {
        if (!saved.getRequestHash().equals(requestHash)) {
            throw new ConflictException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "A Idempotency-Key informada já foi usada com outro conteúdo nas últimas 24 horas. Para criar"
                            + " uma nova solicitação, gere uma nova chave.");
        }
        return new IdempotentResponse(saved.getResponseStatus(), saved.getResponseBody(), true);
    }

    String hash(Object payload) {
        byte[] canonical = json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", ex);
        }
    }
}
