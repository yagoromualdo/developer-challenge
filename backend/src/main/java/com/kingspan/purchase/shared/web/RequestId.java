package com.kingspan.purchase.shared.web;

import org.slf4j.MDC;

/** Constantes e acesso ao identificador de correlação da requisição corrente. */
public final class RequestId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestId() {}

    /** Id da requisição em andamento, ou {@code null} fora de uma requisição HTTP. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
