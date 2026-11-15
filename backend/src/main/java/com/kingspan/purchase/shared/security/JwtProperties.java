package com.kingspan.purchase.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração do JWT, validada na subida para falhar cedo com segredo fraco. */
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String secret, Duration ttl) {

    // HS256 exige chave de pelo menos 256 bits (RFC 7518, seção 3.2).
    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        Objects.requireNonNull(secret, "app.security.jwt.secret (JWT_SECRET) é obrigatório");
        Objects.requireNonNull(ttl, "app.security.jwt.ttl (JWT_TTL_MINUTES) é obrigatório");
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT_SECRET precisa ter pelo menos " + MIN_SECRET_BYTES + " bytes");
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("JWT_TTL_MINUTES precisa ser positivo");
        }
    }

    byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
