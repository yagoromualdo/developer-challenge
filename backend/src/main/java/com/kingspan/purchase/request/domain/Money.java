package com.kingspan.purchase.request.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário em BRL, sempre com escala 2 e nunca negativo.
 *
 * <p>Valores com mais de 2 casas decimais significativas são rejeitados em vez de arredondados: arredondar em
 * silêncio mudaria o total que o solicitante informou. Zeros à direita ({@code 10.500}) não contam como casas
 * significativas.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount é obrigatório");
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException(
                    "Valor monetário com mais de " + SCALE + " casas decimais: " + amount.toPlainString());
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Valor monetário não pode ser negativo: " + amount.toPlainString());
        }
        // Nenhum arredondamento acontece aqui: a checagem acima garante que a escala cabe em 2 casas.
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        Objects.requireNonNull(amount, "amount é obrigatório");
        return new Money(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other é obrigatório");
        return new Money(amount.add(other.amount));
    }

    /** Valor de um item: preço unitário × quantidade. */
    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantidade não pode ser negativa: " + quantity);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        Objects.requireNonNull(other, "other é obrigatório");
        return amount.compareTo(other.amount);
    }

    /** Representação usada no JSON e nas mensagens: {@code "4899.90"}. */
    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
