package com.kingspan.purchase.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /** Fonte única de tempo (UTC); injetada para permitir testes determinísticos. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
