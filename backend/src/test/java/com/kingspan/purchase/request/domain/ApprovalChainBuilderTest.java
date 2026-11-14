package com.kingspan.purchase.request.domain;

import static com.kingspan.purchase.request.domain.StepLevel.ADMIN;
import static com.kingspan.purchase.request.domain.StepLevel.NIVEL_1;
import static com.kingspan.purchase.request.domain.StepLevel.NIVEL_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class ApprovalChainBuilderTest {

    private static final List<String> BOUNDARY_TOTALS =
            List.of("0.01", "999.99", "1000.00", "1000.01", "9999.99", "10000.00", "10000.01", "250000.00");

    private final ApprovalChainBuilder builder = ApprovalChainBuilder.standard();

    @ParameterizedTest(name = "{0} em EQUIPMENT → {1}")
    @CsvSource({
        "0.01,      NIVEL_1",
        "999.99,    NIVEL_1",
        "1000.00,   NIVEL_1",
        "1000.01,   NIVEL_1 NIVEL_2",
        "9999.99,   NIVEL_1 NIVEL_2",
        "10000.00,  NIVEL_1 NIVEL_2",
        "10000.01,  NIVEL_1 NIVEL_2 ADMIN",
        "250000.00, NIVEL_1 NIVEL_2 ADMIN"
    })
    void deveMontarCadeiaPorFaixaDeValorNosLimitesExatos(String total, String expected) {
        assertThat(builder.build(Money.of(total), Category.EQUIPMENT)).containsExactlyElementsOf(levels(expected));
    }

    @ParameterizedTest(name = "{0} em SOFTWARE → {1}")
    @CsvSource({
        "0.01,      NIVEL_1 ADMIN",
        "1000.00,   NIVEL_1 ADMIN",
        "1000.01,   NIVEL_1 NIVEL_2 ADMIN",
        "9999.99,   NIVEL_1 NIVEL_2 ADMIN",
        "10000.00,  NIVEL_1 NIVEL_2 ADMIN",
        "10000.01,  NIVEL_1 NIVEL_2 ADMIN",
        "250000.00, NIVEL_1 NIVEL_2 ADMIN"
    })
    void deveTerminarComUmaUnicaEtapaAdminQuandoCategoriaForSoftware(String total, String expected) {
        assertThat(builder.build(Money.of(total), Category.SOFTWARE)).containsExactlyElementsOf(levels(expected));
    }

    @Test
    void deveExigirAdminEmSoftwareAbaixoDeDezMilMesmoSemExigenciaPorValor() {
        List<StepLevel> software = builder.build(Money.of("9999.99"), Category.SOFTWARE);
        List<StepLevel> equipment = builder.build(Money.of("9999.99"), Category.EQUIPMENT);

        assertThat(equipment).doesNotContain(ADMIN);
        assertThat(software).containsExactly(NIVEL_1, NIVEL_2, ADMIN);
    }

    @Test
    void naoDeveDuplicarAdminEmSoftwareAcimaDeDezMilQuandoAsDuasRegrasExigemAdmin() {
        Money total = Money.of("10000.01");
        assertThat(new ThresholdRule().requiredLevels(total, Category.SOFTWARE)).contains(ADMIN);
        assertThat(new SoftwareRequiresAdminRule().requiredLevels(total, Category.SOFTWARE))
                .containsExactly(ADMIN);

        assertThat(builder.build(total, Category.SOFTWARE))
                .containsExactly(NIVEL_1, NIVEL_2, ADMIN)
                .containsOnlyOnce(ADMIN);
    }

    @ParameterizedTest(name = "{0} em {1}")
    @MethodSource("todasAsCategoriasEmTodosOsLimites")
    void deveGerarCadeiaSemEtapasRepetidasComNivel1PrimeiroEAdminSempreNoFim(String total, Category category) {
        List<StepLevel> chain = builder.build(Money.of(total), category);

        assertThat(chain).doesNotHaveDuplicates().startsWith(NIVEL_1);
        assertThat(chain).isSortedAccordingTo(StepLevel::compareTo);
        if (chain.contains(ADMIN)) {
            assertThat(chain).endsWith(ADMIN).containsOnlyOnce(ADMIN);
        }
        assertThat(chain.contains(ADMIN))
                .as("ADMIN só aparece em SOFTWARE ou acima de 10.000,00")
                .isEqualTo(category == Category.SOFTWARE || Money.of(total).isGreaterThan(Money.of("10000.00")));
    }

    static Stream<Arguments> todasAsCategoriasEmTodosOsLimites() {
        return BOUNDARY_TOTALS.stream()
                .flatMap(total -> Arrays.stream(Category.values()).map(category -> Arguments.of(total, category)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"EQUIPMENT", "SERVICES", "SUPPLIES", "TRAVEL", "OTHER"})
    void naoDeveExigirAdminAbaixoDeDezMilParaCategoriasQueNaoSaoSoftware(Category category) {
        assertThat(builder.build(Money.of("10000.00"), category)).containsExactly(NIVEL_1, NIVEL_2);
    }

    @Test
    void deveManterAOrdemDaCadeiaIndependenteDaOrdemDasRegras() {
        ApprovalChainBuilder reversed =
                new ApprovalChainBuilder(List.of(new SoftwareRequiresAdminRule(), new ThresholdRule()));

        assertThat(reversed.build(Money.of("500.00"), Category.SOFTWARE)).containsExactly(NIVEL_1, ADMIN);
        assertThat(reversed.build(Money.of("20000.00"), Category.SOFTWARE)).containsExactly(NIVEL_1, NIVEL_2, ADMIN);
    }

    @Test
    void deveDevolverCadeiaImutavel() {
        List<StepLevel> chain = builder.build(Money.of("500.00"), Category.SUPPLIES);

        assertThatThrownBy(() -> chain.add(ADMIN)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deveFalharQuandoNenhumaRegraExigeEtapa() {
        ApprovalChainBuilder onlySoftware = new ApprovalChainBuilder(List.of(new SoftwareRequiresAdminRule()));

        assertThatThrownBy(() -> onlySoftware.build(Money.of("500.00"), Category.TRAVEL))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveRejeitarConfiguracaoSemRegras() {
        assertThatThrownBy(() -> new ApprovalChainBuilder(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void softwareRequiresAdminRuleNaoDeveExigirNadaParaOutrasCategorias() {
        SoftwareRequiresAdminRule rule = new SoftwareRequiresAdminRule();

        assertThat(rule.requiredLevels(Money.of("50000.00"), Category.EQUIPMENT))
                .isEmpty();
        assertThat(rule.requiredLevels(Money.of("0.01"), Category.SOFTWARE)).containsExactly(ADMIN);
    }

    private static List<StepLevel> levels(String names) {
        return Arrays.stream(names.trim().split("\s+")).map(StepLevel::valueOf).toList();
    }
}
