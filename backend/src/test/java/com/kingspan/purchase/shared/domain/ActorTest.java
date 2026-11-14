package com.kingspan.purchase.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ActorTest {

    @Test
    void deveExigirNivelParaAprovador() {
        assertThatThrownBy(() -> new Actor(1L, Role.APROVADOR, 10L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approverLevel");
    }

    @Test
    void devePermitirAdminGlobalSemCentroDeCusto() {
        var admin = new Actor(1L, Role.ADMIN, null, null);

        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.isApprover()).isFalse();
    }

    @Test
    void deveExigirUsuarioEPapel() {
        assertThatThrownBy(() -> new Actor(null, Role.SOLICITANTE, 10L, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Actor(1L, null, 10L, null)).isInstanceOf(NullPointerException.class);
    }
}
