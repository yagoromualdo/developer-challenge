package com.kingspan.purchase.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void deveNormalizarParaDuasCasasDecimais() {
        assertThat(Money.of("10").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of("10").amount().scale()).isEqualTo(2);
        assertThat(Money.of("4899.9")).hasToString("4899.90");
    }

    @Test
    void deveConsiderarIguaisValoresComEscalasDiferentes() {
        assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
        assertThat(Money.of("10.5")).isEqualTo(Money.of("10.50"));
        assertThat(Money.of("10").hashCode()).isEqualTo(Money.of("10.00").hashCode());
    }

    @Test
    void deveSomarValores() {
        assertThat(Money.of("0.10").plus(Money.of("0.20"))).isEqualTo(Money.of("0.30"));
        assertThat(Money.of("999.99").plus(Money.of("0.01"))).isEqualTo(Money.of("1000.00"));
        assertThat(Money.ZERO.plus(Money.of("5.55"))).isEqualTo(Money.of("5.55"));
    }

    @Test
    void deveMultiplicarPelaQuantidade() {
        assertThat(Money.of("4899.90").times(5)).isEqualTo(Money.of("24499.50"));
        assertThat(Money.of("0.01").times(3)).isEqualTo(Money.of("0.03"));
        assertThat(Money.of("12.34").times(1)).isEqualTo(Money.of("12.34"));
    }

    @Test
    void deveCalcularTotalDeItensSemPerderPrecisao() {
        record Item(Money unitPrice, int quantity) {}
        var items = List.of(
                new Item(Money.of("4899.90"), 5), new Item(Money.of("0.10"), 3), new Item(Money.of("19.99"), 7));

        Money total = items.stream()
                .map(item -> item.unitPrice().times(item.quantity()))
                .reduce(Money.ZERO, Money::plus);

        assertThat(total).isEqualTo(Money.of("24639.73"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.001", "0.005", "4899.999", "1.123456"})
    void deveRejeitarMaisDeDuasCasasDecimais(String value) {
        assertThatThrownBy(() -> Money.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais de 2 casas decimais");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.500", "10.000", "0.10000"})
    void deveAceitarZerosADireitaAlemDaSegundaCasa(String value) {
        assertThat(Money.of(value).amount().scale()).isEqualTo(2);
    }

    @Test
    void deveRejeitarValorNegativo() {
        assertThatThrownBy(() -> Money.of("-0.01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void deveRejeitarQuantidadeNegativa() {
        assertThatThrownBy(() -> Money.of("1.00").times(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarValorNulo() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of((String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveCompararValores() {
        assertThat(Money.of("1000.01").isGreaterThan(Money.of("1000.00"))).isTrue();
        assertThat(Money.of("1000.00").isGreaterThan(Money.of("1000"))).isFalse();
        assertThat(Money.of("0.00").isZero()).isTrue();
        assertThat(Money.of("1").compareTo(Money.of("2"))).isNegative();
    }
}
