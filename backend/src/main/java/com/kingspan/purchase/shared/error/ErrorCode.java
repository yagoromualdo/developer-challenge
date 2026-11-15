package com.kingspan.purchase.shared.error;

/** Códigos estáveis devolvidos no campo {@code code} dos Problem Details. */
public enum ErrorCode {
    // 400
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    BAD_REQUEST,
    // 401 / 403
    UNAUTHORIZED,
    INVALID_CREDENTIALS,
    ACCESS_DENIED,
    // 404 / 405 / 415
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    // 409
    CONCURRENT_MODIFICATION,
    // 422 — regras de negócio
    INVALID_TRANSITION,
    SELF_APPROVAL_FORBIDDEN,
    APPROVER_NOT_ELIGIBLE,
    DUPLICATE_APPROVER,
    BUDGET_EXCEEDED,
    BUDGET_BELOW_CONSUMED,
    COMMENT_REQUIRED,
    NOT_REQUEST_OWNER,
    EMAIL_ALREADY_REGISTERED,
    COST_CENTER_NOT_FOUND,
    // 500
    INTERNAL_ERROR;

    /** Código genérico para respostas de erro do framework que não têm código específico. */
    static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONCURRENT_MODIFICATION;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
        };
    }

    String typeSlug() {
        return name().toLowerCase().replace('_', '-');
    }
}
