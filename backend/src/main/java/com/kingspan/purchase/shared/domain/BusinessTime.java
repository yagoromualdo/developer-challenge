package com.kingspan.purchase.shared.domain;

import java.time.ZoneId;

/** Fuso das regras de calendário (data "futura" do {@code neededBy}, mês de competência); o armazenamento é UTC. */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private BusinessTime() {}
}
