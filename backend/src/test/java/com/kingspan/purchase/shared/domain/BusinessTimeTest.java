package com.kingspan.purchase.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BusinessTimeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "2026-12-01T02:59:59Z, 2026-11",
        "2026-12-01T03:00:00Z, 2026-12",
        "2027-01-01T02:00:00Z, 2026-12",
        "2026-11-15T12:00:00Z, 2026-11"
    })
    void deveCalcularACompetenciaNoFusoDeSaoPauloENaoEmUtc(String submittedAt, String expected) {
        assertThat(BusinessTime.competenceOf(Instant.parse(submittedAt))).isEqualTo(YearMonth.parse(expected));
    }
}
