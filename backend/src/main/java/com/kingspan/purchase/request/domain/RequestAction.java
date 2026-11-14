package com.kingspan.purchase.request.domain;

/**
 * Ações sobre uma solicitação. Os nomes são exatamente os valores de {@code allowedActions} consumidos pelo
 * front-end; não renomeie.
 */
public enum RequestAction {
    EDIT,
    SUBMIT,
    APPROVE,
    REJECT,
    REQUEST_CHANGES,
    CANCEL
}
