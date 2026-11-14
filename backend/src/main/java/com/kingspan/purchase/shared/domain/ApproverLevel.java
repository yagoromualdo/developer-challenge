package com.kingspan.purchase.shared.domain;

/** Nível de um {@link Role#APROVADOR}. Um {@code NIVEL_2} também aprova etapas {@code NIVEL_1}. */
public enum ApproverLevel {
    NIVEL_1,
    NIVEL_2
}
