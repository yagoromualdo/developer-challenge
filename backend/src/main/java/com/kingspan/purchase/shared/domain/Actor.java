package com.kingspan.purchase.shared.domain;

import java.util.Objects;

/**
 * Quem executa uma ação, como o domínio o enxerga. É montado a partir das claims do JWT na borda da aplicação; o
 * domínio nunca acessa o contexto de segurança.
 *
 * @param costCenterId pode ser {@code null} para um {@code ADMIN} global
 * @param approverLevel obrigatório para {@code APROVADOR}; ignorado pelas regras nos demais papéis
 */
public record Actor(Long userId, Role role, Long costCenterId, ApproverLevel approverLevel) {

    public Actor {
        Objects.requireNonNull(userId, "userId é obrigatório");
        Objects.requireNonNull(role, "role é obrigatório");
        if (role == Role.APROVADOR && approverLevel == null) {
            throw new IllegalArgumentException("APROVADOR precisa de approverLevel");
        }
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isApprover() {
        return role == Role.APROVADOR;
    }
}
