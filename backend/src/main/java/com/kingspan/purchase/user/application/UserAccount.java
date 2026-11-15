package com.kingspan.purchase.user.application;

import com.kingspan.purchase.shared.domain.Actor;
import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.user.infra.UserEntity;

/** Visão do usuário que sai da camada de aplicação; não tem campo de senha, então o hash nunca chega à API. */
public record UserAccount(
        Long id, String name, String email, Role role, ApproverLevel approverLevel, Long costCenterId) {

    static UserAccount from(UserEntity user) {
        return new UserAccount(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getApproverLevel(),
                user.getCostCenterId());
    }

    Actor toActor() {
        return new Actor(id, role, costCenterId, approverLevel);
    }
}
