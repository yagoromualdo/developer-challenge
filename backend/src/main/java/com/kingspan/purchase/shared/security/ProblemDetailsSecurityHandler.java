package com.kingspan.purchase.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Entry point (401) e access denied handler (403) da cadeia de segurança. As falhas acontecem nos filtros, fora do
 * Spring MVC, então são repassadas ao {@code @RestControllerAdvice}: o formato do Problem Details fica num lugar só.
 */
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public ProblemDetailsSecurityHandler(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        resolve(request, response, ex, HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        resolve(request, response, ex, HttpServletResponse.SC_FORBIDDEN);
    }

    private void resolve(HttpServletRequest request, HttpServletResponse response, Exception ex, int fallbackStatus)
            throws IOException {
        if (resolver.resolveException(request, response, null, ex) == null) {
            response.sendError(fallbackStatus);
        }
    }
}
