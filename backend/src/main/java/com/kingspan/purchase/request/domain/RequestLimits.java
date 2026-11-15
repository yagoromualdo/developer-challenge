package com.kingspan.purchase.request.domain;

/**
 * Limites de entrada da solicitação. Os de texto são os tamanhos das colunas da V1 (um teste do schema confere); os
 * numéricos garantem que o total de 100 itens cabe em {@code NUMERIC(19,2)}: 100 × 1.000.000 × 999.999.999,99 &lt;
 * 10^17.
 */
public final class RequestLimits {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    public static final int COMMENT_MAX_LENGTH = 2000;
    public static final int ITEM_DESCRIPTION_MAX_LENGTH = 500;

    public static final int MAX_ITEMS = 100;
    public static final int MAX_ITEM_QUANTITY = 1_000_000;
    public static final int UNIT_PRICE_INTEGER_DIGITS = 9;

    private RequestLimits() {}
}
