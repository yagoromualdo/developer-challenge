package com.kingspan.purchase.request.domain;

/** Estados da solicitação de compra (README, seção 4.1). */
public enum RequestStatus {
    DRAFT,
    PENDING_APPROVAL,
    CHANGES_REQUESTED,
    APPROVED,
    REJECTED,
    CANCELLED;

    /** Estados finais não aceitam nenhuma ação. */
    public boolean isFinal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }
}
