package com.kingspan.purchase.request.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Resposta salva do {@code POST /requests} para o replay por {@code Idempotency-Key} (ADR 006). */
@Entity
@Immutable
@Table(name = "idempotency_key")
public class IdempotencyKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // "key" é palavra reservada no HQL (função de mapa); o campo tem outro nome para as consultas derivadas.
    @Column(name = "key", nullable = false)
    private String idempotencyKey;

    // SHA-256 em hex tem tamanho fixo: a coluna é char(64), e não varchar.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {}

    public IdempotencyKeyEntity(
            Long userId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody,
            Instant createdAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.responseStatus = responseStatus;
        this.responseBody = Objects.requireNonNull(responseBody, "responseBody");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
