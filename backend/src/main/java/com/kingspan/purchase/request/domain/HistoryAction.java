package com.kingspan.purchase.request.domain;

/**
 * Ação registrada no histórico ({@code request_event.action}): as de {@link RequestAction} mais {@code CREATE}, que
 * não é uma transição. O CHECK {@code ck_request_event_action} (V2) espelha este enum: ao mudá-lo, crie uma migration.
 */
public enum HistoryAction {
    CREATE,
    EDIT,
    SUBMIT,
    APPROVE,
    REJECT,
    REQUEST_CHANGES,
    CANCEL;

    public static HistoryAction of(RequestAction action) {
        return switch (action) {
            case EDIT -> EDIT;
            case SUBMIT -> SUBMIT;
            case APPROVE -> APPROVE;
            case REJECT -> REJECT;
            case REQUEST_CHANGES -> REQUEST_CHANGES;
            case CANCEL -> CANCEL;
        };
    }
}
