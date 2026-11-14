package com.kingspan.purchase.request.domain;

/**
 * Nível de uma etapa da cadeia de aprovação. É distinto de {@link
 * com.kingspan.purchase.shared.domain.ApproverLevel}: a etapa {@code ADMIN} não corresponde a nenhum nível de
 * aprovador, só ao papel {@code ADMIN}.
 *
 * <p>A ordem de declaração <strong>é</strong> a ordem de aprovação na cadeia: o {@link ApprovalChainBuilder} ordena
 * as etapas por ela. Não reordene as constantes.
 */
public enum StepLevel {
    NIVEL_1,
    NIVEL_2,
    ADMIN
}
