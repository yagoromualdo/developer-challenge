package com.kingspan.purchase.user.web;

import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import jakarta.validation.constraints.Positive;

/** PATCH parcial: campo ausente mantém o valor atual. Nome, e-mail e senha não são editáveis por aqui. */
record ChangeUserProfileRequest(
        Role role,
        ApproverLevel approverLevel,
        @Positive(message = "deve ser positivo") Long costCenterId) {}
