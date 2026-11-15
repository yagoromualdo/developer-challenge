package com.kingspan.purchase.request.application;

import com.kingspan.purchase.request.domain.ActionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Uma única instância da {@link ActionPolicy} para validar ações, montar {@code allowedActions} e filtrar a caixa. */
@Configuration(proxyBeanMethods = false)
class RequestPolicyConfig {

    @Bean
    ActionPolicy actionPolicy() {
        return ActionPolicy.standard();
    }
}
