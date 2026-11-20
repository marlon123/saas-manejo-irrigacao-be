package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.CycleDay;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.waterbalance.WaterBalanceCalculator;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 10: round trip de persistência do balanço.
 *
 * <p>Para qualquer balanço calculado pelo {@link WaterBalanceCalculator}, converter para a entidade e
 * voltar ao domínio preserva todos os campos, comparados com {@code compareTo}, sem perda de casas
 * (inclusive a escala 13 de AFDi/AFDf e a escala 10 do tempo de funcionamento).
 *
 * <p><b>Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.11</b>
 */
class BalancePersistenceRoundTripPropertyTest {

    private final DailyWaterBalancePersistenceMapper mapper = new DailyWaterBalancePersistenceMapper();
    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    // Feature: irrigation-water-balance-module, Property 10: Round trip de persistência do balanço
    @Property(tries = 300)
    void persistenceRoundTripPreservesEveryField(@ForAll("balances") DailyWaterBalance original) {
        DailyWaterBalance restored = mapper.toDomain(mapper.toEntity(original));

        // identificação
        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.areaId()).isEqualTo(original.areaId());
        assertThat(restored.date()).isEqualTo(original.date());
        assertThat(restored.cropId()).isEqualTo(original.cropId());
        assertThat(restored.soilProfileId()).isEqualTo(original.soilProfileId());
        assertThat(restored.managementPlanId()).isEqualTo(original.managementPlanId());
        assertThat(restored.cycleDay()).isEqualTo(original.cycleDay());

        // entradas
        WaterBalanceInputs a = restored.inputs();
        WaterBalanceInputs e = original.inputs();
        same(a.etp().value(), e.etp().value(), "ETP");
        same(a.precipitation().value(), e.precipitation().value(), "P");
        same(a.appliedIrrigation().value(), e.appliedIrrigation().value(), "IR");
        same(a.fieldCapacity().value(), e.fieldCapacity().value(), "CC");
        same(a.permanentWiltingPoint().value(), e.permanentWiltingPoint().value(), "PMP");
        same(a.bulkDensity().value(), e.bulkDensity().value(), "D");
        same(a.rootDepth().value(), e.rootDepth().value(), "Z");
        same(a.kc().value(), e.kc().value(), "Kc");
        same(a.availabilityFactor().value(), e.availabilityFactor().value(), "F");
        same(a.sprinklerRate().value(), e.sprinklerRate().value(), "taxa");
        assertThat(a.firstDayOfPlan()).isEqualTo(e.firstDayOfPlan());
        if (e.previousAfdFinal() == null) {
            assertThat(a.previousAfdFinal()).isNull();
        } else {
            same(a.previousAfdFinal(), e.previousAfdFinal(), "AFDf anterior");
        }

        // resultados
        WaterBalanceResult ra = restored.result();
        WaterBalanceResult re = original.result();
        same(ra.etc(), re.etc(), "ETc");
        same(ra.cad(), re.cad(), "CAD");
        same(ra.afdInitial(), re.afdInitial(), "AFDi");
        same(ra.afdFinal(), re.afdFinal(), "AFDf");
        same(ra.idealIrrigation(), re.idealIrrigation(), "I");
        same(ra.operatingTimeHours(), re.operatingTimeHours(), "tempo");
        assertThat(ra.recommended()).isEqualTo(re.recommended());
        assertThat(ra.noRecommendationReason()).isEqualTo(re.noRecommendationReason());

        // sem perda de casas: a escala é preservada exatamente
        assertThat(ra.afdInitial().scale()).isEqualTo(re.afdInitial().scale());
        assertThat(ra.afdFinal().scale()).isEqualTo(re.afdFinal().scale());
        assertThat(ra.operatingTimeHours().scale()).isEqualTo(re.operatingTimeHours().scale());

        // controle
        assertThat(restored.calculatedAt()).isEqualTo(original.calculatedAt());
        assertThat(restored.methodologyId()).isEqualTo(original.methodologyId());
        assertThat(restored.methodologyVersion()).isEqualTo(original.methodologyVersion());
        assertThat(restored.revision()).isEqualTo(original.revision());
        assertThat(restored.current()).isEqualTo(original.current());
    }

    private static void same(BigDecimal actual, BigDecimal expected, String field) {
        assertThat(actual).as(field).isNotNull();
        assertThat(actual.compareTo(expected))
                .as("%s: esperado %s, obtido %s", field, expected, actual)
                .isZero();
    }

    @Provide
    Arbitrary<DailyWaterBalance> balances() {
        return Combinators.combine(inputs(), Arbitraries.integers().between(1, 400),
                        Arbitraries.integers().between(1, 50), Arbitraries.of(true, false))
                .as((in, cycleDay, revision, current) -> new DailyWaterBalance(
                        UUID.randomUUID(), UUID.randomUUID(),
                        LocalDate.of(2026, 1, 1).plusDays(cycleDay),
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        new CycleDay(cycleDay), in, calculator.calculate(in),
                        Instant.parse("2026-03-10T12:34:56.789Z").plusMillis(cycleDay * 1000L),
                        "water-balance", "1.0.0", revision, current));
    }

    private Arbitrary<WaterBalanceInputs> inputs() {
        Arbitrary<BigDecimal> depth = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000")).ofScale(2);
        Arbitrary<BigDecimal> kc = Arbitraries.integers().between(1, 200).map(i -> BigDecimal.valueOf(i, 2));
        Arbitrary<BigDecimal> f = Arbitraries.integers().between(1, 10_000).map(i -> BigDecimal.valueOf(i, 4));
        Arbitrary<BigDecimal> rate = Arbitraries.integers().between(1, 5_000_000)
                .map(i -> BigDecimal.valueOf(i, 4));
        // PMP < CC, ambos em % com escala 2
        Arbitrary<BigDecimal> pmp = Arbitraries.integers().between(100, 3000).map(i -> BigDecimal.valueOf(i, 2));
        Arbitrary<BigDecimal> ccDelta = Arbitraries.integers().between(100, 3000)
                .map(i -> BigDecimal.valueOf(i, 2));
        Arbitrary<BigDecimal> density = Arbitraries.integers().between(800, 1900)
                .map(i -> BigDecimal.valueOf(i, 3));
        Arbitrary<BigDecimal> root = Arbitraries.integers().between(10_000, 200_000)
                .map(i -> BigDecimal.valueOf(i, 2));
        Arbitrary<BigDecimal> previousAfdFinal = Arbitraries.bigDecimals()
                .between(new BigDecimal("-5000"), new BigDecimal("5000")).ofScale(13);

        Arbitrary<BigDecimal[]> soil = Combinators.combine(pmp, ccDelta, density, root)
                .as((p, d, bd, z) -> new BigDecimal[]{p.add(d), p, bd, z});
        Arbitrary<BigDecimal[]> weather = Combinators.combine(depth, depth, depth)
                .as((etp, p, ir) -> new BigDecimal[]{etp, p, ir});
        Arbitrary<BigDecimal[]> crop = Combinators.combine(kc, f, rate)
                .as((k, ff, r) -> new BigDecimal[]{k, ff, r});

        return Combinators.combine(soil, weather, crop, previousAfdFinal, Arbitraries.of(true, false))
                .as((s, w, c, prev, firstDay) -> new WaterBalanceInputs(
                        new EtpDepth(w[0]),
                        new PrecipitationDepth(w[1]),
                        new AppliedIrrigationDepth(w[2]),
                        new GravimetricWaterContent(s[0]),
                        new GravimetricWaterContent(s[1]),
                        new BulkDensity(s[2]),
                        new RootDepth(s[3]),
                        new Kc(c[0]),
                        new AvailabilityFactor(c[1]),
                        new SprinklerRate(c[2]),
                        firstDay,
                        firstDay ? null : prev));
    }
}
