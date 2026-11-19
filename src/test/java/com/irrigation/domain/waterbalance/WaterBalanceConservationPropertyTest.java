package com.irrigation.domain.waterbalance;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitrary;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 2: conservação do saldo de água.
 *
 * <p>Para qualquer entrada válida e qualquer AFDi (inclusive AFDf acima do CAD ou negativa):
 * {@code AFDf − AFDi = P + IR − ETc}, {@code I = AFDi − AFDf = ETc − P − IR}, sem truncamento.
 *
 * <p><b>Validates: Requirements 8.5, 8.6, 9.1, 15.2</b>
 */
class WaterBalanceConservationPropertyTest {

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    @Provide
    Arbitrary<WaterBalanceInputs> anyDayInputs() {
        return Arbitraries.of(true, false).flatMap(WaterBalanceArbitraries::inputs);
    }

    // Feature: irrigation-water-balance-module, Property 2: Conservação do saldo de água
    @Property(tries = 300)
    void balanceIsConservedWithoutClamping(@ForAll("anyDayInputs") WaterBalanceInputs in) {
        WaterBalanceResult r = calculator.calculate(in);

        BigDecimal p = in.precipitation().value();
        BigDecimal ir = in.appliedIrrigation().value();
        BigDecimal etc = in.kc().value().multiply(in.etp().value());
        BigDecimal delta = p.add(ir).subtract(etc);

        assertThat(r.etc().compareTo(etc)).isZero();
        assertThat(r.afdFinal().subtract(r.afdInitial()).compareTo(delta)).isZero();
        assertThat(r.afdFinal().compareTo(r.afdInitial().add(delta))).isZero();
        assertThat(r.idealIrrigation().compareTo(r.afdInitial().subtract(r.afdFinal()))).isZero();
        assertThat(r.idealIrrigation().compareTo(etc.subtract(p).subtract(ir))).isZero();
    }

    // Feature: irrigation-water-balance-module, Property 2: Conservação do saldo de água
    @Property(tries = 200)
    void afdFinalIsNeverClamped(@ForAll("anyDayInputs") WaterBalanceInputs in) {
        WaterBalanceResult r = calculator.calculate(in);
        BigDecimal expected = r.afdInitial()
                .add(in.precipitation().value())
                .add(in.appliedIrrigation().value())
                .subtract(in.kc().value().multiply(in.etp().value()));

        // Sem limite superior (CAD) nem inferior (zero): o valor bruto é preservado.
        assertThat(r.afdFinal().compareTo(expected)).isZero();
    }

}
