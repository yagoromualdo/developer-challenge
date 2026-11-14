package com.kingspan.purchase.request.domain;

/** Categoria da compra (README, seção 2.2). {@code SOFTWARE} sempre exige etapa {@code ADMIN}. */
public enum Category {
    EQUIPMENT,
    SERVICES,
    SUPPLIES,
    TRAVEL,
    SOFTWARE,
    OTHER
}
