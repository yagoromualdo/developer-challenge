package com.kingspan.purchase.request.domain;

import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.Objects;

/** {@code reason} vem sem o prefixo da ação; a {@link ActionPolicy} monta a mensagem completa. */
public record RuleViolation(ErrorCode code, String reason) {

    public RuleViolation {
        Objects.requireNonNull(code, "code é obrigatório");
        Objects.requireNonNull(reason, "reason é obrigatório");
    }
}
