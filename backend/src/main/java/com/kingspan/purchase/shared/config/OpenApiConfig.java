package com.kingspan.purchase.shared.config;

import com.kingspan.purchase.shared.domain.Actor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // Actor vem do token (ActorArgumentResolver), não da requisição: não deve aparecer como parâmetro na doc.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class);
    }

    @Bean
    OpenAPI purchaseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Purchase Requests API")
                        .version("v1")
                        .description("Solicitações de compra com orçamento por centro de custo e cadeia de"
                                + " aprovação sequencial. Erros seguem Problem Details (RFC 9457) com `code`."))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
