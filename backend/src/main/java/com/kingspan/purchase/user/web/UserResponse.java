package com.kingspan.purchase.user.web;

import com.kingspan.purchase.shared.domain.ApproverLevel;
import com.kingspan.purchase.shared.domain.Role;
import com.kingspan.purchase.user.application.UserAccount;

record UserResponse(Long id, String name, String email, Role role, ApproverLevel approverLevel, Long costCenterId) {

    static UserResponse from(UserAccount account) {
        return new UserResponse(
                account.id(),
                account.name(),
                account.email(),
                account.role(),
                account.approverLevel(),
                account.costCenterId());
    }
}
