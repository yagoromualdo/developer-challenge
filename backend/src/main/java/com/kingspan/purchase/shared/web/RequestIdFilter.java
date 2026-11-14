package com.kingspan.purchase.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garante um {@code X-Request-Id} por requisição: reaproveita o recebido (se for seguro) ou gera um UUID,
 * coloca no MDC (sai em todo log estruturado) e devolve no header da resposta.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    // Restringe o valor recebido para evitar injeção de conteúdo em logs e headers.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(ATTRIBUTE, requestId);
        // Header definido antes da cadeia para estar presente mesmo se a resposta for confirmada cedo.
        response.setHeader(RequestId.HEADER, requestId);
        MDC.put(RequestId.MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
    }

    // O dispatch de erro do container acontece depois que o MDC foi limpo; reaplicamos o mesmo id.
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private static String resolveRequestId(HttpServletRequest request) {
        Object alreadyAssigned = request.getAttribute(ATTRIBUTE);
        if (alreadyAssigned instanceof String id) {
            return id;
        }
        String received = request.getHeader(RequestId.HEADER);
        if (received != null && SAFE_REQUEST_ID.matcher(received).matches()) {
            return received;
        }
        return UUID.randomUUID().toString();
    }
}
