package com.kingspan.purchase.shared.error;

/** Item de {@code errors[]} nas respostas 400 ({@code field} no formato {@code items[0].quantity}). */
public record FieldViolation(String field, String message) {}
