package com.kingspan.purchase.request.domain;

/**
 * Ações sobre uma solicitação. Os nomes são exatamente os valores de {@code allowedActions} consumidos pelo
 * front-end; não renomeie.
 */
public enum RequestAction {
    EDIT("editar a solicitação"),
    SUBMIT("submeter a solicitação"),
    APPROVE("aprovar a solicitação"),
    REJECT("rejeitar a solicitação"),
    REQUEST_CHANGES("devolver a solicitação para ajuste"),
    CANCEL("cancelar a solicitação");

    private final String phrase;

    RequestAction(String phrase) {
        this.phrase = phrase;
    }

    public boolean isStepDecision() {
        return this == APPROVE || this == REJECT || this == REQUEST_CHANGES;
    }

    public boolean requiresComment() {
        return this == REJECT || this == REQUEST_CHANGES;
    }

    /** Usado nas mensagens de erro: "Não é possível {@code phrase}: motivo". */
    public String phrase() {
        return phrase;
    }
}
