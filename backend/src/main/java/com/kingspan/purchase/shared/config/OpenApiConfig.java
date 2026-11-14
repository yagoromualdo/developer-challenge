package com.kingspan.purchase.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI purchaseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Purchase Requests API")
                        .version("v1")
                        .description("Solicitações de compra com orçamento por centro de custo e cadeia de"
                                + " aprovação sequencial. Erros seguem Problem Details (RFC 9457) com `code`."));
    }
}
