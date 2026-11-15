package com.kingspan.purchase.shared.config;

import com.kingspan.purchase.shared.domain.BusinessTime;
import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ValidationConfig {

    /**
     * {@code @Future} usa o bean {@link Clock} (testável) no fuso de negócio: às 22h em São Paulo já é amanhã em UTC,
     * e o "amanhã" do usuário não pode ser rejeitado como data passada.
     */
    @Bean
    ValidationConfigurationCustomizer businessClockProvider(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock.withZone(BusinessTime.ZONE));
    }
}
