package com.kingspan.purchase.request.domain;

/** Ação registrada no histórico: as de {@link RequestAction} mais {@code CREATE}, que não é uma transição. */
public enum HistoryAction {
    CREATE,
    EDIT,
    SUBMIT,
    APPROVE,
    REJECT,
    REQUEST_CHANGES,
    CANCEL
}
