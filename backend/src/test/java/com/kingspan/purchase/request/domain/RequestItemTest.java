package com.kingspan.purchase.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestItemTest {

    @Test
    void deveCalcularOSubtotalComoQuantidadeVezesPrecoUnitario() {
        assertThat(new RequestItem("Notebook", 5, Money.of("4899.90")).subtotal())
                .isEqualTo(Money.of("24499.50"));
    }

    @Test
    void deveSomarOsSubtotaisNoTotal() {
        Money total = RequestItem.totalOf(List.of(
                new RequestItem("Notebook", 5, Money.of("4899.90")), new RequestItem("Mouse", 3, Money.of("0.01"))));

        assertThat(total).isEqualTo(Money.of("24499.53"));
    }

    @Test
    void deveRejeitarQuantidadeEPrecoQueNaoSejamPositivos() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestItem("Notebook", 0, Money.of("1.00")));
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestItem("Notebook", 1, Money.ZERO));
    }

    @Test
    void deveCaberEmNumeric19Casas2NoPiorCasoPermitidoPelosLimites() {
        Money maxUnitPrice = Money.of("999999999.99");
        List<RequestItem> items = Collections.nCopies(
                RequestLimits.MAX_ITEMS, new RequestItem("Item", RequestLimits.MAX_ITEM_QUANTITY, maxUnitPrice));

        // NUMERIC(19,2) guarda até 17 dígitos inteiros.
        assertThat(RequestItem.totalOf(items).amount().precision() - 2).isLessThanOrEqualTo(17);
    }
}
