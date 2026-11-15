package com.kingspan.purchase.shared.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/** Fuso das regras de calendário (data "futura" do {@code neededBy}, mês de competência); o armazenamento é UTC. */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private BusinessTime() {}

    /** Mês de competência de uma submissão: 01/12 às 01h em UTC ainda é novembro em São Paulo. */
    public static YearMonth competenceOf(Instant submittedAt) {
        Objects.requireNonNull(submittedAt, "submittedAt");
        return YearMonth.from(submittedAt.atZone(ZONE));
    }
}
