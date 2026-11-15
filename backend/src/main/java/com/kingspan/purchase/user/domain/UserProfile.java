package com.kingspan.purchase.user.domain;

import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.shared.error.BusinessRuleException;
import com.kingspan.purchase.shared.error.ErrorCode;
import java.util.Objects;

/**
 * Papel, nível e centro de custo de um usuário, sempre coerentes entre si: nível só para APROVADOR (e obrigatório
 * nele) e centro de custo obrigatório fora de ADMIN. Espelha os CHECKs de {@code app_user}, mas com 422 explicativo.
 */
public record UserProfile(Role role, ApproverLevel approverLevel, Long costCenterId) {

    public UserProfile {
        Objects.requireNonNull(role, "role é obrigatório");
        if (role == Role.APROVADOR && approverLevel == null) {
            throw new BusinessRuleException(
                    ErrorCode.APPROVER_LEVEL_REQUIRED,
                    "Um APROVADOR precisa de approverLevel (NIVEL_1 ou NIVEL_2) para saber quais etapas pode aprovar.");
        }
        if (role != Role.APROVADOR && approverLevel != null) {
            throw new BusinessRuleException(
                    ErrorCode.APPROVER_LEVEL_NOT_ALLOWED,
                    "approverLevel só se aplica ao papel APROVADOR; o papel " + role + " não aprova por nível.");
        }
        if (role != Role.ADMIN && costCenterId == null) {
            throw new BusinessRuleException(
                    ErrorCode.COST_CENTER_REQUIRED,
                    "O papel " + role + " precisa de um centro de custo; só o ADMIN pode ser global.");
        }
    }

    /**
     * Aplica uma edição parcial: campo {@code null} mantém o valor atual. Ao sair de APROVADOR o nível é descartado,
     * para que rebaixar alguém não exija enviar {@code approverLevel: null} explicitamente.
     */
    public UserProfile change(Role newRole, ApproverLevel newLevel, Long newCostCenterId) {
        Role role = newRole != null ? newRole : this.role;
        ApproverLevel level = newLevel;
        if (level == null && role == Role.APROVADOR) {
            level = this.approverLevel;
        }
        Long costCenter = newCostCenterId != null ? newCostCenterId : this.costCenterId;
        return new UserProfile(role, level, costCenter);
    }
}
