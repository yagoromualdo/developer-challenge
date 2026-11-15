package com.kingspan.purchase.shared.security;

import com.kingspan.purchase.shared.domain.Actor;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injeta o {@link Actor} autenticado em parâmetros de controller; o domínio nunca acessa o contexto de segurança. */
@Component
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtService jwtService;

    public ActorArgumentResolver(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Actor resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtService.actorFrom(jwtAuthentication.getToken());
        }
        // Só acontece num endpoint público que peça Actor: erro de configuração, respondido como 401.
        throw new AuthenticationCredentialsNotFoundException("Nenhum token de acesso na requisição");
    }
}
