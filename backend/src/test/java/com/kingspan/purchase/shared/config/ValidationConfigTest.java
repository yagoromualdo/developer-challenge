package com.kingspan.purchase.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Configuration;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Future;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ValidationConfigTest {

    // 01:00 UTC de 15/11 = 22:00 de 14/11 em São Paulo.
    private static final Clock LATE_EVENING_IN_SAO_PAULO =
            Clock.fixed(Instant.parse("2026-11-15T01:00:00Z"), ZoneOffset.UTC);

    record Payload(@Future LocalDate neededBy) {}

    @Test
    void deveAvaliarDataFuturaPeloClockNoFusoDeSaoPaulo() {
        try (ValidatorFactory factory = factoryWith(LATE_EVENING_IN_SAO_PAULO)) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(new Payload(LocalDate.of(2026, 11, 15))))
                    .isEmpty();
            assertThat(validator.validate(new Payload(LocalDate.of(2026, 11, 14))))
                    .hasSize(1);
        }
    }

    private static ValidatorFactory factoryWith(Clock clock) {
        Configuration<?> configuration = Validation.byDefaultProvider().configure();
        new ValidationConfig().businessClockProvider(clock).customize(configuration);
        return configuration.buildValidatorFactory();
    }
}
