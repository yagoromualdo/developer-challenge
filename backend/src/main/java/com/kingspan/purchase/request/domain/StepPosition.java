package com.kingspan.purchase.request.domain;

/**
 * Posição da etapa atual na cadeia de aprovação. Só influencia o destino de {@link RequestAction#APPROVE}: aprovar uma
 * etapa intermediária mantém {@code PENDING_APPROVAL}; aprovar a última leva a {@code APPROVED}.
 */
public enum StepPosition {
    INTERMEDIATE,
    LAST
}
