package com.kingspan.purchase.request.domain;

public enum StepStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CHANGES_REQUESTED;

    /** Status em que a etapa atual fica depois da decisão do aprovador. */
    public static StepStatus decidedBy(RequestAction action) {
        return switch (action) {
            case APPROVE -> APPROVED;
            case REJECT -> REJECTED;
            case REQUEST_CHANGES -> CHANGES_REQUESTED;
            case EDIT, SUBMIT, CANCEL -> throw new IllegalArgumentException(action + " não decide uma etapa");
        };
    }
}
