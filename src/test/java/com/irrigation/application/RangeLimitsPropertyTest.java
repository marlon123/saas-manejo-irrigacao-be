package com.irrigation.application;

import com.irrigation.application.dto.AppliedIrrigationInput;
import com.irrigation.application.dto.AreaInput;
import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.CropStageInput;
import com.irrigation.application.dto.DailyWeatherInput;
import com.irrigation.application.dto.ManagementPlanInput;
import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.service.AppliedIrrigationService;
import com.irrigation.application.service.AreaService;
import com.irrigation.application.service.CropService;
import com.irrigation.application.service.DailyWeatherService;
import com.irrigation.application.service.ManagementPlanService;
import com.irrigation.application.service.SoilProfileService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.InMemoryAppliedIrrigationRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryDailyWeatherRepository;
import com.irrigation.support.InMemoryManagementPlanRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.RangeSpec;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.PropertyDefaults;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * Propriedade 11: limites dos value objects e dos cadastros.
 *
 * <p>No domínio, cada value object aceita o valor se e somente se ele está na faixa e na escala dos requisitos
 * (e rejeita com {@link IllegalArgumentException}). Nos serviços de cadastro (área, cultura, solo), de plano, de
 * meteorologia e de irrigação real, um valor fora da faixa gera uma violação com o nome do campo da API e nenhuma
 * persistência ocorre.
 */
@PropertyDefaults(tries = 200)
class RangeLimitsPropertyTest {

    /** Escala máxima (casas decimais) de cada value object que a impõe no próprio construtor. */
    private static final Map<String, Integer> VALUE_OBJECT_SCALE = Map.of(
            "GravimetricWaterContent", 2,
            "AvailabilityFactor", 4,
            "SprinklerRate", 4,
            "EtpDepth", 2,
            "PrecipitationDepth", 2,
            "AppliedIrrigationDepth", 2);

    // =====================================================================================
    // Geradores
    // =====================================================================================

    /** Valores na escala 0,01 em torno da faixa, com as bordas sobre-representadas. */
    private static Arbitrary<BigDecimal> valuesAround(RangeSpec spec) {
        List<BigDecimal> borders = new ArrayList<>(List.of(
                spec.lowestValid(), spec.highestBelow(), spec.min()));
        if (spec.max() != null) {
            borders.add(spec.max());
            borders.add(spec.lowestAbove());
        }
        BigDecimal upper = spec.max() != null ? spec.max() : spec.min().add(new BigDecimal("1000"));
        long lowCents = spec.min().movePointRight(2).longValueExact() - 300;
        long highCents = upper.movePointRight(2).longValueExact() + 300;
        Arbitrary<BigDecimal> random = Arbitraries.longs().between(lowCents, highCents)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        return Arbitraries.frequencyOf(
                Tuple.of(3, Arbitraries.of(borders)),
                Tuple.of(7, random));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<RangeSpec, BigDecimal>> specsAndValues() {
        return Arbitraries.of(RangeSpec.ALL).flatMap(spec -> valuesAround(spec).map(v -> Tuple.of(spec, v)));
    }

    @Provide
    Arbitrary<RangeSpec> specs() {
        return Arbitraries.of(RangeSpec.ALL);
    }

    @Provide
    Arbitrary<Tuple.Tuple2<RangeSpec, BigDecimal>> specsWithExcessScale() {
        Arbitrary<RangeSpec> scaled = Arbitraries.of(
                RangeSpec.ALL.stream().filter(s -> VALUE_OBJECT_SCALE.containsKey(s.name())).toList());
        return scaled.flatMap(spec -> Arbitraries.longs().between(0, 20000)
                .flatMap(base -> Arbitraries.integers().between(1, 9).map(digit -> {
                    int scale = VALUE_OBJECT_SCALE.get(spec.name());
                    BigDecimal value = spec.lowestValid()
                            .add(BigDecimal.valueOf(base, 2))
                            .add(BigDecimal.valueOf(digit, scale + 1));
                    return Tuple.of(spec, value);
                })));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<Scenario, BigDecimal>> scenariosAndValues() {
        return Arbitraries.of(SCENARIOS).flatMap(s -> valuesAround(s.spec()).map(v -> Tuple.of(s, v)));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<Scenario, BigDecimal>> scenariosWithExcessScale() {
        Arbitrary<Scenario> scaled = Arbitraries.of(SCENARIOS.stream().filter(s -> s.maxScale() != null).toList());
        return scaled.flatMap(s -> Arbitraries.longs().between(0, 20000)
                .flatMap(base -> Arbitraries.integers().between(1, 9).map(digit -> {
                    BigDecimal value = s.spec().lowestValid()
                            .add(BigDecimal.valueOf(base, 2))
                            .add(BigDecimal.valueOf(digit, s.maxScale() + 1));
                    return Tuple.of(s, value);
                })));
    }

    // =====================================================================================
    // Domínio
    // =====================================================================================

    // Feature: irrigation-water-balance-module, Property 11: Limites dos value objects e dos cadastros
    @Property
    void valueObjectAcceptsAValueIfAndOnlyIfItIsWithinTheRange(
            @ForAll("specsAndValues") Tuple.Tuple2<RangeSpec, BigDecimal> input) {
        RangeSpec spec = input.get1();
        BigDecimal value = input.get2();

        if (spec.accepts(value)) {
            assertThat(spec.factory().apply(value)).as("%s aceita %s", spec, value).isNotNull();
        } else {
            assertThatThrownBy(() -> spec.factory().apply(value))
                    .as("%s rejeita %s", spec, value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // Feature: irrigation-water-balance-module, Property 11: Limites dos value objects e dos cadastros
    @Property
    void valueObjectAcceptsTheBordersAndRejectsTheValuesJustOutsideThem(@ForAll("specs") RangeSpec spec) {
        assertThat(spec.factory().apply(spec.lowestValid())).isNotNull();
        if (spec.max() != null) {
            assertThat(spec.factory().apply(spec.max())).isNotNull();
            assertThatThrownBy(() -> spec.factory().apply(spec.lowestAbove()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> spec.factory().apply(spec.highestBelow()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Feature: irrigation-water-balance-module, Property 11: Limites dos value objects e dos cadastros
    @Property
    void valueObjectRejectsMoreDecimalPlacesThanTheScaleAllows(
            @ForAll("specsWithExcessScale") Tuple.Tuple2<RangeSpec, BigDecimal> input) {
        RangeSpec spec = input.get1();
        BigDecimal value = input.get2();
        Assume.that(spec.accepts(value));

        assertThatThrownBy(() -> spec.factory().apply(value))
                .as("%s rejeita %s (casas decimais a mais)", spec, value)
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =====================================================================================
    // Serviços
    // =====================================================================================

    // Feature: irrigation-water-balance-module, Property 11: Limites dos value objects e dos cadastros
    @Property
    void servicesRejectOutOfRangeValuesWithTheFieldNameAndPersistNothing(
            @ForAll("scenariosAndValues") Tuple.Tuple2<Scenario, BigDecimal> input) {
        Scenario scenario = input.get1();
        BigDecimal value = input.get2();

        Outcome outcome = scenario.run().apply(value);

        if (!scenario.spec().accepts(value)) {
            assertThat(outcome.violationFields())
                    .as("%s com %s deve gerar violação em %s", scenario, value, scenario.field())
                    .contains(scenario.field());
            assertThat(outcome.persisted()).as("%s com %s não deve persistir", scenario, value).isFalse();
        } else if (scenario.acceptedIsCheckable().test(value)) {
            assertThat(outcome.violationFields())
                    .as("%s com %s está na faixa", scenario, value)
                    .doesNotContain(scenario.field());
            if (outcome.violationFields().isEmpty()) {
                assertThat(outcome.persisted()).as("%s com %s deve persistir", scenario, value).isTrue();
            }
        }
        if (!outcome.violationFields().isEmpty()) {
            assertThat(outcome.persisted()).as("violação não pode persistir").isFalse();
        }
    }

    // Feature: irrigation-water-balance-module, Property 11: Limites dos value objects e dos cadastros
    @Property
    void servicesRejectValuesWithMoreDecimalPlacesThanAllowed(
            @ForAll("scenariosWithExcessScale") Tuple.Tuple2<Scenario, BigDecimal> input) {
        Scenario scenario = input.get1();
        BigDecimal value = input.get2();
        Assume.that(scenario.spec().accepts(value));

        Outcome outcome = scenario.run().apply(value);

        assertThat(outcome.violationFields())
                .as("%s com %s (casas decimais a mais)", scenario, value)
                .contains(scenario.field());
        assertThat(outcome.persisted()).isFalse();
    }

    // =====================================================================================
    // Cenários dos serviços
    // =====================================================================================

    /** Resultado de uma chamada de serviço: campos com violação e se o estado persistido mudou. */
    record Outcome(Set<String> violationFields, boolean persisted) {
    }

    /**
     * Um campo numérico de um serviço, a faixa do value object correspondente e a chamada que o exercita
     * com os demais campos válidos.
     *
     * @param acceptedIsCheckable se o valor na faixa pode ser conferido sem interferência de outra regra
     *                            (por exemplo, a relação CC &gt; PMP)
     */
    record Scenario(String label, String field, RangeSpec spec, Integer maxScale,
                    Predicate<BigDecimal> acceptedIsCheckable, Function<BigDecimal, Outcome> run) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static Outcome attempt(Supplier<Object> snapshot, Runnable call) {
        Object before = snapshot.get();
        Set<String> fields = Set.of();
        try {
            call.run();
        } catch (ValidationFailedException e) {
            fields = e.getViolations().stream().map(FieldViolation::field).collect(Collectors.toSet());
        }
        return new Outcome(fields, !before.equals(snapshot.get()));
    }

    private static RangeSpec spec(String name) {
        return RangeSpec.ALL.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static final Predicate<BigDecimal> ALWAYS = v -> true;

    private static final List<Scenario> SCENARIOS = buildScenarios();

    private static List<Scenario> buildScenarios() {
        List<Scenario> list = new ArrayList<>();

        // ---- Área (Req 3.2, 3.3 do módulo base)
        list.add(areaScenario("areaHectares", "AreaInHectares",
                (in, v) -> new AreaInput(in.name(), v, in.latitude(), in.longitude(), in.generalCharacteristics(),
                        in.irrigationSystemType(), in.systemFlowRate(), in.irrigationEfficiency())));
        list.add(areaScenario("systemFlowRate", "FlowRate",
                (in, v) -> new AreaInput(in.name(), in.areaHectares(), in.latitude(), in.longitude(),
                        in.generalCharacteristics(), in.irrigationSystemType(), v, in.irrigationEfficiency())));
        list.add(areaScenario("irrigationEfficiency", "IrrigationEfficiency",
                (in, v) -> new AreaInput(in.name(), in.areaHectares(), in.latitude(), in.longitude(),
                        in.generalCharacteristics(), in.irrigationSystemType(), in.systemFlowRate(), v)));

        // ---- Cultura (Req 2.2, 2.3, 2.10)
        list.add(cropScenario("kc", "Kc", 2, (in, v) ->
                new CropInput(in.name(), in.variety(), in.cultivationCycleDays(), in.stages(), v, in.rootDepth())));
        list.add(cropScenario("rootDepthMm", "RootDepth", 2, (in, v) ->
                new CropInput(in.name(), in.variety(), in.cultivationCycleDays(), in.stages(), in.kc(), v)));
        list.add(cropScenario("stages[0].kc", "Kc", null, (in, v) ->
                new CropInput(in.name(), in.variety(), in.cultivationCycleDays(),
                        List.of(new CropStageInput("Inicial", v, 1)), in.kc(), in.rootDepth())));

        // ---- Solo (Req 1.2, 1.3, 1.9, 3.2, 3.3)
        list.add(soilScenario("bulkDensity", "BulkDensity", null, ALWAYS, (in, v) -> soil(in, null, null,
                null, null, v, in.effectiveRootDepth(), in.availableWater())));
        list.add(soilScenario("effectiveRootDepth", "RootDepth", null, ALWAYS, (in, v) -> soil(in, null, null,
                null, null, in.bulkDensity(), v, in.availableWater())));
        list.add(soilScenario("availableWater", "AvailableWater", null, ALWAYS, (in, v) -> soil(in, null, null,
                null, null, in.bulkDensity(), in.effectiveRootDepth(), v)));
        // Pares CC/PMP: o valor sob teste vem com o par completo; CC > PMP limita onde a aceitação é conferível.
        list.add(soilScenario("fieldCapacity", "VolumetricWaterContent", null, v -> v.signum() > 0,
                (in, v) -> soil(in, v, BigDecimal.ZERO, null, null, in.bulkDensity(), null, null)));
        list.add(soilScenario("permanentWiltingPoint", "VolumetricWaterContent", null,
                v -> v.compareTo(BigDecimal.ONE) < 0,
                (in, v) -> soil(in, BigDecimal.ONE, v, null, null, in.bulkDensity(), null, null)));
        list.add(soilScenario("fieldCapacityGravimetricPct", "GravimetricWaterContent", 2, v -> v.signum() > 0,
                (in, v) -> soil(in, null, null, v, BigDecimal.ZERO, in.bulkDensity(), null, null)));
        list.add(soilScenario("permanentWiltingPointGravimetricPct", "GravimetricWaterContent", 2,
                v -> v.compareTo(bd("100")) < 0,
                (in, v) -> soil(in, null, null, bd("100"), v, in.bulkDensity(), null, null)));

        // ---- Plano de manejo (Req 4.3, 4.4)
        list.add(planCreateScenario("fFactor", "AvailabilityFactor", 4,
                v -> new PlanValues(v, bd("10"))));
        list.add(planCreateScenario("sprinklerRateMmPerHour", "SprinklerRate", 4,
                v -> new PlanValues(bd("0.5"), v)));
        list.add(planUpdateScenario("fFactor", "AvailabilityFactor", 4,
                v -> new PlanValues(v, bd("10"))));
        list.add(planUpdateScenario("sprinklerRateMmPerHour", "SprinklerRate", 4,
                v -> new PlanValues(bd("0.5"), v)));

        // ---- Meteorologia diária (Req 5.2, 5.5)
        list.add(weatherScenario("etpMm", "EtpDepth", (etp, p) -> TestData.dailyWeatherInput(
                TestData.TODAY.minusDays(1), etp.toPlainString(), p.toPlainString())));
        list.add(weatherScenario("precipitationMm", "PrecipitationDepth", (p, etp) -> TestData.dailyWeatherInput(
                TestData.TODAY.minusDays(1), etp.toPlainString(), p.toPlainString())));

        // ---- Irrigação real (Req 6.2, 6.3)
        list.add(irrigationScenario());

        return List.copyOf(list);
    }

    // ---- Área

    private static Scenario areaScenario(String field, String specName,
                                         java.util.function.BiFunction<AreaInput, BigDecimal, AreaInput> withValue) {
        return new Scenario("Área." + field, field, spec(specName), null, ALWAYS, value -> {
            InMemoryRecommendationRepository recommendations = new InMemoryRecommendationRepository();
            InMemoryAreaRepository areas = new InMemoryAreaRepository(recommendations);
            AreaService service = new AreaService(areas);
            AreaInput input = withValue.apply(TestData.areaInput(), value);
            return attempt(areas::count, () -> service.create(input));
        });
    }

    // ---- Cultura

    private static Scenario cropScenario(String field, String specName, Integer maxScale,
                                         java.util.function.BiFunction<CropInput, BigDecimal, CropInput> withValue) {
        return new Scenario("Cultura." + field, field, spec(specName), maxScale, ALWAYS, value -> {
            CropRepository repository = mock(CropRepository.class);
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            CropService service = new CropService(repository);
            CropInput input = withValue.apply(TestData.cropInput(), value);
            return attempt(() -> writes(repository), () -> service.create(input));
        });
    }

    // ---- Solo

    private static Scenario soilScenario(String field, String specName, Integer maxScale,
                                         Predicate<BigDecimal> checkable,
                                         java.util.function.BiFunction<SoilProfileInput, BigDecimal,
                                                 SoilProfileInput> withValue) {
        return new Scenario("Solo." + field, field, spec(specName), maxScale, checkable, value -> {
            SoilProfileRepository repository = mock(SoilProfileRepository.class);
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            SoilProfileService service = new SoilProfileService(repository);
            SoilProfileInput input = withValue.apply(TestData.soilProfileInput(), value);
            return attempt(() -> writes(repository), () -> service.create(input));
        });
    }

    private static SoilProfileInput soil(SoilProfileInput base, BigDecimal fieldCapacity, BigDecimal wiltingPoint,
                                         BigDecimal fieldCapacityGravimetric, BigDecimal wiltingPointGravimetric,
                                         BigDecimal bulkDensity, BigDecimal rootDepth, BigDecimal availableWater) {
        return new SoilProfileInput(base.soilType(), fieldCapacity, wiltingPoint, fieldCapacityGravimetric, wiltingPointGravimetric,
                bulkDensity, rootDepth, availableWater, base.soilWaterUnit(), base.bulkDensityUnit(),
                base.rootDepthUnit(), base.availableWaterUnit());
    }

    /** Quantidade de chamadas de escrita (save e deleteById) recebidas pelo repositório simulado. */
    private static Object writes(Object repository) {
        return mockingDetails(repository).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("save") || i.getMethod().getName().equals("deleteById"))
                .count();
    }

    // ---- Plano de manejo

    private record PlanValues(BigDecimal factor, BigDecimal rate) {
    }

    private static ManagementPlanService planService(InMemoryManagementPlanRepository plans,
                                                     InMemoryAreaRepository areas) {
        CropRepository crops = mock(CropRepository.class);
        SoilProfileRepository soils = mock(SoilProfileRepository.class);
        when(crops.existsById(any())).thenReturn(true);
        when(soils.existsById(any())).thenReturn(true);
        return new ManagementPlanService(plans, areas, crops, soils);
    }

    private static Scenario planCreateScenario(String field, String specName, Integer maxScale,
                                               Function<BigDecimal, PlanValues> values) {
        return new Scenario("Plano.criar." + field, field, spec(specName), maxScale, ALWAYS, value -> {
            InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
            InMemoryManagementPlanRepository plans = new InMemoryManagementPlanRepository();
            Area area = areas.save(TestData.area());
            ManagementPlanService service = planService(plans, areas);
            PlanValues pv = values.apply(value);
            ManagementPlanInput input = new ManagementPlanInput(UUID.randomUUID(), UUID.randomUUID(),
                    TestData.TODAY.minusDays(10), null, pv.factor(), pv.rate());
            return attempt(plans::count, () -> service.create(area.getId(), input));
        });
    }

    private static Scenario planUpdateScenario(String field, String specName, Integer maxScale,
                                               Function<BigDecimal, PlanValues> values) {
        return new Scenario("Plano.atualizar." + field, field, spec(specName), maxScale, ALWAYS, value -> {
            InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
            InMemoryManagementPlanRepository plans = new InMemoryManagementPlanRepository();
            Area area = areas.save(TestData.area());
            ManagementPlan plan = plans.save(TestData.managementPlan(
                    area.getId(), UUID.randomUUID(), UUID.randomUUID(), TestData.TODAY.minusDays(10)));
            ManagementPlanService service = planService(plans, areas);
            PlanValues pv = values.apply(value);
            ManagementPlanInput input = new ManagementPlanInput(null, null, null, null, pv.factor(), pv.rate());
            // O plano guardado é identificado pela instância: uma gravação o substitui por outro objeto.
            Supplier<Object> snapshot = () -> List.of(plans.count(),
                    System.identityHashCode(plans.findById(plan.getId()).orElseThrow()));
            return attempt(snapshot, () -> service.update(area.getId(), plan.getId(), input));
        });
    }

    // ---- Meteorologia diária

    private static Scenario weatherScenario(String field, String specName,
                                            java.util.function.BiFunction<BigDecimal, BigDecimal, DailyWeatherInput>
                                                    inputFactory) {
        return new Scenario("Meteorologia." + field, field, spec(specName), 2, ALWAYS, value -> {
            InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
            InMemoryDailyWeatherRepository weather = new InMemoryDailyWeatherRepository();
            Area area = areas.save(TestData.area());
            DailyWeatherService service = new DailyWeatherService(weather, areas, TestData.fixedClock());
            DailyWeatherInput input = inputFactory.apply(value, bd("5.00"));
            return attempt(weather::count, () -> service.upsert(area.getId(), input));
        });
    }

    // ---- Irrigação real

    private static Scenario irrigationScenario() {
        return new Scenario("IrrigaçãoReal.appliedIrrigationMm", "appliedIrrigationMm",
                spec("AppliedIrrigationDepth"), 2, ALWAYS, value -> {
            InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
            InMemoryAppliedIrrigationRepository irrigations = new InMemoryAppliedIrrigationRepository();
            Area area = areas.save(TestData.area());
            AppliedIrrigationService service =
                    new AppliedIrrigationService(irrigations, areas, TestData.fixedClock());
            AppliedIrrigationInput input = new AppliedIrrigationInput(TestData.TODAY.minusDays(1), value);
            return attempt(irrigations::count, () -> service.upsert(area.getId(), input));
        });
    }
}
