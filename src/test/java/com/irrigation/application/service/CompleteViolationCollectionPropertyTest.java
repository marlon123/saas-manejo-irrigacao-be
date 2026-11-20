package com.irrigation.application.service;

import com.irrigation.application.dto.AppliedIrrigationInput;
import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.CropStageInput;
import com.irrigation.application.dto.DailyWeatherInput;
import com.irrigation.application.dto.ManagementPlanInput;
import com.irrigation.application.dto.ManagementPlanOutput;
import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.FakeAreaOperationLock;
import com.irrigation.support.InMemoryAppliedIrrigationRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryDailyWaterBalanceRepository;
import com.irrigation.support.InMemoryDailyWeatherRepository;
import com.irrigation.support.InMemoryManagementPlanRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.irrigation.support.TestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/**
 * Propriedade 13: coleta completa de violações.
 *
 * <p>Requisições de plano, meteorologia, irrigação real, cálculo, solo e cultura com subconjuntos arbitrários
 * de campos inválidos ou ausentes (inclusive o corpo inteiro ausente) resultam em {@link ValidationFailedException} cujas violações são
 * exatamente o conjunto de campos inválidos: nenhum a menos, nenhum a mais e sem duplicatas, sempre com o
 * nome do campo da API. Nada é persistido.
 *
 * <p>**Validates: Requirements 4.5, 5.4, 5.5, 6.2, 6.6, 7.4, 1.9, 2.10**
 */
class CompleteViolationCollectionPropertyTest {

    private static final LocalDate BASE = LocalDate.of(2026, 1, 1);

    // ------------------------------------------------------------------ geradores

    /** Valor de um campo e se ele deve gerar violação. */
    private record Opt<T>(T value, boolean invalid) {
    }

    /** Entrada gerada e o conjunto exato de campos que devem ser reportados. */
    private record Case<I>(I input, Set<String> expected) {
    }

    private static BigDecimal bd(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static List<BigDecimal> decimals(String... values) {
        List<BigDecimal> list = new ArrayList<>();
        for (String v : values) {
            list.add(bd(v));
        }
        return list;
    }

    /** Sorteia entre valores válidos e inválidos (a lista pode conter nulo), com a mesma chance de cada lado. */
    private static <T> Arbitrary<Opt<T>> opt(List<T> valid, List<T> invalid) {
        Arbitrary<Opt<T>> ok = Arbitraries.integers().between(0, valid.size() - 1)
                .map(i -> new Opt<>(valid.get(i), false));
        Arbitrary<Opt<T>> bad = Arbitraries.integers().between(0, invalid.size() - 1)
                .map(i -> new Opt<>(invalid.get(i), true));
        return Arbitraries.oneOf(ok, bad);
    }

    private static void mark(Set<String> expected, String field, Opt<?> o) {
        if (o.invalid()) {
            expected.add(field);
        }
    }

    private static Arbitrary<Opt<BigDecimal>> decimalOpt(String[] valid, String[] invalid) {
        return opt(decimals(valid), decimals(invalid));
    }

    private static Arbitrary<Opt<LocalDate>> pastOrTodayDate() {
        Arbitrary<Opt<LocalDate>> ok = Arbitraries.integers().between(0, 60)
                .map(i -> new Opt<>(TODAY.minusDays(i), false));
        Arbitrary<Opt<LocalDate>> future = Arbitraries.integers().between(1, 60)
                .map(i -> new Opt<>(TODAY.plusDays(i), true));
        Arbitrary<Opt<LocalDate>> missing = Arbitraries.just(new Opt<>(null, true));
        return Arbitraries.oneOf(ok, future, missing);
    }

    private static Arbitrary<Opt<UUID>> uuidOpt() {
        return Arbitraries.oneOf(
                Arbitraries.create(() -> new Opt<>(UUID.randomUUID(), false)),
                Arbitraries.just(new Opt<UUID>(null, true)));
    }

    /** Par "ambos ou nenhum" com CC maior que PMP; cada modo diz quais campos devem ser reportados. */
    private record Pair(BigDecimal cc, BigDecimal pmp, Set<String> expected) {
    }

    private static Arbitrary<Pair> pair(String ccName, String pmpName, String validCc, String validPmp,
                                        String... badValues) {
        BigDecimal cc = bd(validCc);
        BigDecimal pmp = bd(validPmp);
        return Combinators.combine(Arbitraries.integers().between(0, 10),
                        Arbitraries.of(badValues).map(BigDecimal::new), Arbitraries.of(badValues).map(BigDecimal::new))
                .as((mode, badA, badB) -> switch (mode) {
                    case 0 -> new Pair(cc, pmp, Set.of());
                    case 1 -> new Pair(null, null, Set.of());
                    case 2 -> new Pair(badA, pmp, Set.of(ccName));
                    case 3 -> new Pair(cc, badA, Set.of(pmpName));
                    case 4 -> new Pair(badA, badB, Set.of(ccName, pmpName));
                    case 5 -> new Pair(cc, null, Set.of(ccName, pmpName));
                    case 6 -> new Pair(null, pmp, Set.of(ccName, pmpName));
                    case 7 -> new Pair(pmp, pmp, Set.of(ccName));
                    case 8 -> new Pair(pmp, cc, Set.of(ccName));
                    case 9 -> new Pair(badA, null, Set.of(ccName, pmpName));
                    default -> new Pair(null, badA, Set.of(ccName, pmpName));
                });
    }

    // ---------------------------------------------------------------- verificação

    private static void assertExactViolations(Supplier<?> action, Set<String> expected) {
        assertThatThrownBy(action::get).isInstanceOfSatisfying(ValidationFailedException.class, e -> {
            List<String> fields = e.getViolations().stream().map(FieldViolation::field).toList();
            assertThat(fields).as("sem duplicatas").doesNotHaveDuplicates();
            assertThat(new HashSet<>(fields)).as("campos reportados").isEqualTo(expected);
        });
    }

    // ------------------------------------------------------------- plano de manejo

    private record PlanFixture(InMemoryManagementPlanRepository plans, InMemoryAreaRepository areas,
                               ManagementPlanService service) {
        static PlanFixture create() {
            InMemoryManagementPlanRepository plans = new InMemoryManagementPlanRepository();
            InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
            CropRepository crops = mock(CropRepository.class);
            SoilProfileRepository soils = mock(SoilProfileRepository.class);
            when(crops.existsById(any())).thenReturn(true);
            when(soils.existsById(any())).thenReturn(true);
            return new PlanFixture(plans, areas, new ManagementPlanService(plans, areas, crops, soils));
        }
    }

    private record PlanRequest(UUID areaId, ManagementPlanInput input) {
    }

    @Provide
    Arbitrary<Case<PlanRequest>> planCreateRequests() {
        return Combinators.combine(
                        uuidOpt(), uuidOpt(), uuidOpt(),
                        opt(Arrays.asList(0, 1, 30), Arrays.asList((Integer) null)),
                        decimalOpt(new String[]{"0.5", "1", "0.0001", "0.9999"},
                                new String[]{null, "0", "-0.1", "1.01", "0.12345"}),
                        decimalOpt(new String[]{"10", "500", "0.0001", "6.5"},
                                new String[]{null, "0", "-1", "500.01", "1.23456"}))
                .as((area, crop, soil, start, f, rate) -> new Object[]{area, crop, soil, start, f, rate})
                .flatMap(parts -> Arbitraries.integers().between(0, 2).flatMap(endMode ->
                        Arbitraries.integers().between(1, 30).map(endOffset -> toPlanCase(parts, endMode, endOffset))));
    }

    @SuppressWarnings("unchecked")
    private static Case<PlanRequest> toPlanCase(Object[] p, int endMode, int endOffset) {
        Opt<UUID> area = (Opt<UUID>) p[0];
        Opt<UUID> crop = (Opt<UUID>) p[1];
        Opt<UUID> soil = (Opt<UUID>) p[2];
        Opt<Integer> start = (Opt<Integer>) p[3];
        Opt<BigDecimal> f = (Opt<BigDecimal>) p[4];
        Opt<BigDecimal> rate = (Opt<BigDecimal>) p[5];

        LocalDate startDate = start.invalid() ? null : BASE.plusDays(start.value());
        LocalDate endDate = null;
        Set<String> expected = new HashSet<>();
        if (endMode == 1) {
            endDate = BASE.plusDays(start.invalid() ? 0 : start.value()).plusDays(endOffset);
        } else if (endMode == 2) {
            // data final anterior ao início: só é reportada quando o início é válido (Req 4.5)
            endDate = BASE.plusDays(start.invalid() ? 0 : start.value()).minusDays(endOffset);
            if (startDate != null) {
                expected.add("endDate");
            }
        }
        mark(expected, "areaId", area);
        mark(expected, "cropId", crop);
        mark(expected, "soilProfileId", soil);
        mark(expected, "startDate", start);
        mark(expected, "fFactor", f);
        mark(expected, "sprinklerRateMmPerHour", rate);
        return new Case<>(new PlanRequest(area.value(),
                new ManagementPlanInput(crop.value(), soil.value(), startDate, endDate, f.value(), rate.value())),
                expected);
    }

    // Feature: irrigation-water-balance-module, Property 13: Coleta completa de violações
    @Property(tries = 200)
    void planCreationReportsExactlyTheInvalidFields(@ForAll("planCreateRequests") Case<PlanRequest> c) {
        Set<String> expected = c.expected();
        // sem campo inválido a criação é válida e este teste não se aplica
        net.jqwik.api.Assume.that(!expected.isEmpty());
        PlanFixture f = PlanFixture.create();
        UUID areaId = c.input().areaId();
        if (areaId != null) {
            f.areas().save(TestData.area(areaId, "Talhão", "10.50"));
        }

        assertExactViolations(() -> f.service().create(areaId, c.input().input()), expected);

        assertThat(f.plans().count()).isZero();
    }

    @Provide
    Arbitrary<Case<PlanRequest>> planUpdateRequests() {
        return Combinators.combine(uuidOpt(), uuidOpt(),
                        decimalOpt(new String[]{"0.5", "1", "0.0001"}, new String[]{null, "0", "-0.1", "1.01", "0.12345"}),
                        decimalOpt(new String[]{"10", "500", "6.5"}, new String[]{null, "0", "-1", "500.01", "1.23456"}))
                .as((area, plan, f, rate) -> {
                    Set<String> expected = new HashSet<>();
                    mark(expected, "areaId", area);
                    mark(expected, "planId", plan);
                    mark(expected, "fFactor", f);
                    mark(expected, "sprinklerRateMmPerHour", rate);
                    // planId viaja em "areaId" do registro auxiliar apenas para o teste escolher o id do plano
                    return new Case<>(new PlanRequest(area.value(),
                            new ManagementPlanInput(plan.value(), null, null, null, f.value(), rate.value())),
                            expected);
                });
    }

    // Feature: irrigation-water-balance-module, Property 13: Coleta completa de violações
    @Property(tries = 150)
    void planUpdateReportsExactlyTheInvalidFieldsAndKeepsTheStoredPlan(
            @ForAll("planUpdateRequests") Case<PlanRequest> c) {
        net.jqwik.api.Assume.that(!c.expected().isEmpty());
        PlanFixture f = PlanFixture.create();
        UUID realArea = UUID.randomUUID();
        f.areas().save(TestData.area(realArea, "Talhão", "10.50"));
        ManagementPlanOutput seeded = f.service().create(realArea,
                TestData.managementPlanInput(UUID.randomUUID(), UUID.randomUUID(), BASE));
        ManagementPlan before = f.plans().findById(seeded.id()).orElseThrow();

        // o campo cropId do input auxiliar carrega o planId (nulo = planId ausente)
        UUID planId = c.input().input().cropId() == null ? null : seeded.id();
        UUID areaId = c.input().areaId() == null ? null : realArea;
        ManagementPlanInput in = new ManagementPlanInput(null, null, null, null,
                c.input().input().fFactor(), c.input().input().sprinklerRateMmPerHour());

        assertExactViolations(() -> f.service().update(areaId, planId, in), c.expected());

        assertThat(f.plans().count()).isEqualTo(1);
        assertThat(f.plans().findById(seeded.id()).orElseThrow()).isSameAs(before);
    }

    // ----------------------------------------------------------------- meteorologia

    @Provide
    Arbitrary<Case<DailyWeatherInput>> weatherInputs() {
        Arbitrary<Opt<BigDecimal>> depth = decimalOpt(new String[]{"0", "12.34", "1000", "0.01"},
                new String[]{null, "-0.01", "1000.01", "1.234"});
        Arbitrary<Case<DailyWeatherInput>> fields = Combinators.combine(pastOrTodayDate(), depth, depth)
                .as((date, etp, p) -> {
                    Set<String> expected = new HashSet<>();
                    mark(expected, "date", date);
                    mark(expected, "etpMm", etp);
                    mark(expected, "precipitationMm", p);
                    return new Case<>(new DailyWeatherInput(date.value(), etp.value(), p.value()), expected);
                });
        // corpo ausente: todos os campos obrigatórios são reportados
        Case<DailyWeatherInput> noBody = new Case<>(null, Set.of("date", "etpMm", "precipitationMm"));
        return Arbitraries.frequencyOf(Tuple.of(9, fields), Tuple.of(1, Arbitraries.just(noBody)));
    }

    // Feature: irrigation-water-balance-module, Property 13: Coleta completa de violações
    @Property(tries = 200)
    void weatherReportsExactlyTheInvalidFields(@ForAll("weatherInputs") Case<DailyWeatherInput> c) {
        net.jqwik.api.Assume.that(!c.expected().isEmpty());
        InMemoryDailyWeatherRepository repository = new InMemoryDailyWeatherRepository();
        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        DailyWeatherService service = new DailyWeatherService(repository, areas, TestData.fixedClock());
        UUID areaId = areas.save(TestData.area()).getId();

        assertExactViolations(() -> service.upsert(areaId, c.input()), c.expected());

        assertThat(repository.count()).isZero();
    }

    // ------------------------------------------------------------- irrigação real

    @Provide
    Arbitrary<Case<AppliedIrrigationInput>> irrigationInputs() {
        Arbitrary<Opt<BigDecimal>> depth = decimalOpt(new String[]{"0", "12.34", "1000", "0.01"},
                new String[]{null, "-0.01", "1000.01", "1.234"});
        Arbitrary<Case<AppliedIrrigationInput>> fields = Combinators.combine(pastOrTodayDate(), depth)
                .as((date, ir) -> {
                    Set<String> expected = new HashSet<>();
                    mark(expected, "date", date);
                    mark(expected, "appliedIrrigationMm", ir);
                    return new Case<>(new AppliedIrrigationInput(date.value(), ir.value()), expected);
                });
        Case<AppliedIrrigationInput> noBody = new Case<>(null, Set.of("date", "appliedIrrigationMm"));
        return Arbitraries.frequencyOf(Tuple.of(9, fields), Tuple.of(1, Arbitraries.just(noBody)));
    }

    // Feature: irrigation-water-balance-module, Property 13: Coleta completa de violações
    @Property(tries = 200)
    void appliedIrrigationReportsExactlyTheInvalidFields(@ForAll("irrigationInputs") Case<AppliedIrrigationInput> c) {
        net.jqwik.api.Assume.that(!c.expected().isEmpty());
        InMemoryAppliedIrrigationRepository repository = new InMemoryAppliedIrrigationRepository();
        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        AppliedIrrigationService service = new AppliedIrrigationService(repository, areas, TestData.fixedClock());
        UUID areaId = areas.save(TestData.area()).getId();

        assertExactViolations(() -> service.upsert(areaId, c.input()), c.expected());

        assertThat(repository.count()).isZero();
    }

    // -------------------------------------------------------------------- cálculo

    private record CalculationRequest(UUID areaId, CalculateWaterBalanceInput input) {
    }

    @Provide
    Arbitrary<Case<CalculationRequest>> calculationRequests() {
        Arbitrary<Opt<LocalDate>> date = Arbitraries.oneOf(
                Arbitraries.integers().between(0, 400).map(i -> new Opt<>(BASE.plusDays(i), false)),
                Arbitraries.just(new Opt<LocalDate>(null, true)));
        Arbitrary<Opt<Integer>> cycleDay = opt(Arrays.asList(1, 2, 90, 365), Arrays.asList(null, 0, -1, -30));
        // corpo ausente (input nulo) é sorteado junto com os campos
        Arbitrary<Boolean> noBody = Arbitraries.frequency(Tuple.of(1, true), Tuple.of(9, false));
        return Combinators.combine(uuidOpt(), date, cycleDay, noBody).as((area, d, tr, missingBody) -> {
            Set<String> expected = new HashSet<>();
            mark(expected, "areaId", area);
            if (missingBody) {
                expected.add("date");
                expected.add("cycleDay");
                return new Case<>(new CalculationRequest(area.value(), null), expected);
            }
            mark(expected, "date", d);
            mark(expected, "cycleDay", tr);
            return new Case<>(new CalculationRequest(area.value(),
                    new CalculateWaterBalanceInput(d.value(), tr.value())), expected);
        });
    }

    private record CalculationFixture(InMemoryDailyWaterBalanceRepository balances, FakeAreaOperationLock lock,
                                      WaterBalanceService service) {
        static CalculationFixture create() {
            InMemoryDailyWaterBalanceRepository balances = new InMemoryDailyWaterBalanceRepository();
            FakeAreaOperationLock lock = new FakeAreaOperationLock();
            WaterBalanceService service = new WaterBalanceService(
                    new InMemoryAreaRepository(new InMemoryRecommendationRepository()),
                    new InMemoryManagementPlanRepository().connectBalances(balances),
                    mock(SoilProfileRepository.class), mock(CropRepository.class),
                    new InMemoryDailyWeatherRepository(), new InMemoryAppliedIrrigationRepository(),
                    balances, lock, TestData.fixedClock());
            return new CalculationFixture(balances, lock, service);
        }
    }

    // Feature: irrigation-water-balance-module, Property 13: Coleta completa de violações
    @Property(tries = 200)
    void calculationReportsExactlyTheInvalidFields(@ForAll("calculationRequests") Case<CalculationRequest> c) {
        net.jqwik.api.Assume.that(!c.expected().isEmpty());
        CalculationFixture f = CalculationFixture.create();

        assertExactViolations(() -> f.service().calculate(c.input().areaId(), c.input().input()), c.expected());

        assertThat(f.balances().count()).isZero();
        assertThat(f.lock().callCount()).isZero();
    }

    // ----------------------------------------------------------------------- solo

    @Provide
    Arbitrary<Case<SoilProfileInput>> soilInputs() {
        Arbitrary<Opt<String>> soilType = opt(Arrays.asList("Argiloso", "Arenoso"), Arrays.asList(null, "", "   "));
        Arbitrary<Opt<BigDecimal>> density = decimalOpt(new String[]{"1.3", "0.01", "3.00"},
                new String[]{null, "0", "-1", "3.01"});
        Arbitrary<Opt<BigDecimal>> rootDepth = decimalOpt(new String[]{null, "600.00", "1"},
                new String[]{"0", "-5", "5000.01"});
        Arbitrary<Opt<BigDecimal>> availableWater = decimalOpt(new String[]{null, "100.00", "0"},
                new String[]{"-0.01", "-10"});
        return Combinators.combine(soilType,
                        pair("fieldCapacity", "permanentWiltingPoint", "0.35", "0.18", "1.5", "-0.1", "1.01"),
                        pair("fieldCapacityGravimetricPct", "permanentWiltingPointGravimetricPct", "30", "15",
                                "-1", "100.01", "50.123", "200"),
                        density, rootDepth, availableWater)
                .as((type, vol, grav, d, z, aw) -> {
                    Set<String> expected = new HashSet<>();
                    mark(expected, "soilType", type);
                    expected.addAll(vol.expected());
                    expected.addAll(grav.expected());
                    mark(expected, "bulkDensity", d);
                    mark(expected, "effectiveRootDepth", z);
                    mark(expected, "availableWater", aw);
                    return new Case<>(new SoilProfileInput(type.value(), vol.cc(), vol.pmp(), grav.cc(), grav.pmp(),
                            d.value(), z.value(), aw.value(), null, null, null, null), expected);
                });
    }

    // Feature: irrigation-water-balance-module, Property 13: Coleta completa de violações
    @Property(tries = 300)
    void soilProfileReportsExactlyTheInvalidFields(@ForAll("soilInputs") Case<SoilProfileInput> c) {
        net.jqwik.api.Assume.that(!c.expected().isEmpty());
        SoilProfileRepository repository = mock(SoilProfileRepository.class);
        SoilProfileService service = new SoilProfileService(repository);

        assertExactViolations(() -> service.create(c.input()), c.expected());

        verify(repository, never()).save(any());
        verifyNoMoreInteractions(repository);
    }

    // --------------------------------------------------------------------- cultura

    private static String text(int length) {
        return "x".repeat(length);
    }

    private record StageSpec(Opt<String> name, Opt<BigDecimal> kc, Opt<Integer> order) {
    }

    @Provide
    Arbitrary<Case<CropInput>> cropInputs() {
        Arbitrary<Opt<String>> name = opt(Arrays.asList("Milho", text(255)), Arrays.asList(null, "", "  ", text(256)));
        Arbitrary<Opt<String>> variety = opt(Arrays.asList(null, "Híbrido", text(255)), Arrays.asList(text(256)));
        Arbitrary<Opt<BigDecimal>> kc = decimalOpt(new String[]{"0.01", "1.00", "2.00", "0.4"},
                new String[]{null, "0", "-1", "2.01", "1.234"});
        Arbitrary<Opt<BigDecimal>> rootDepth = decimalOpt(new String[]{"1", "400", "5000", "250.5"},
                new String[]{null, "0", "-3", "5000.01", "100.123"});
        Arbitrary<StageSpec> stage = Combinators.combine(
                opt(Arrays.asList("Inicial", "Pleno"), Arrays.asList(null, "", "  ")),
                decimalOpt(new String[]{"0.40", "1.15", "2.0"}, new String[]{null, "0", "-0.5", "2.5"}),
                opt(Arrays.asList(1), Arrays.asList(null, 0, -1)))
                .as(StageSpec::new);
        Arbitrary<List<StageSpec>> stages = Arbitraries.oneOf(
                stage.list().ofMinSize(0).ofMaxSize(4),
                stage.list().ofMinSize(21).ofMaxSize(22));
        return Combinators.combine(name, variety, kc, rootDepth, stages).as((n, v, k, z, specs) -> {
            Set<String> expected = new HashSet<>();
            mark(expected, "name", n);
            mark(expected, "variety", v);
            mark(expected, "kc", k);
            mark(expected, "rootDepthMm", z);
            List<CropStageInput> inputs = new ArrayList<>();
            for (int i = 0; i < specs.size(); i++) {
                StageSpec s = specs.get(i);
                String prefix = "stages[" + i + "]";
                mark(expected, prefix + ".name", s.name());
                mark(expected, prefix + ".kc", s.kc());
                mark(expected, prefix + ".stageOrder", s.order());
                // ordens válidas são únicas (1..n); as inválidas vêm do gerador, inclusive nula
                Integer order = s.order().invalid() ? s.order().value() : Integer.valueOf(i + 1);
                inputs.add(new CropStageInput(s.name().value(), s.kc().value(), order));
            }
            if (specs.size() > 20) {
                expected.add("stages");
            }
            return new Case<>(new CropInput(n.value(), v.value(), 120, inputs, k.value(), z.value()), expected);
        });
    }

    // Feature: irrigation-water-balance-module, Property 13: Coleta completa de violações
    @Property(tries = 300)
    void cropReportsExactlyTheInvalidFields(@ForAll("cropInputs") Case<CropInput> c) {
        net.jqwik.api.Assume.that(!c.expected().isEmpty());
        CropRepository repository = mock(CropRepository.class);
        CropService service = new CropService(repository);

        assertExactViolations(() -> service.create(c.input()), c.expected());

        verify(repository, never()).save(any());
        verifyNoMoreInteractions(repository);
    }
}
