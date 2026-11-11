package com.irrigation.domain;

import com.irrigation.domain.calculation.CalculationEngine;
import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.calculation.exception.DuplicateMethodologyException;
import com.irrigation.domain.calculation.exception.InvalidCalculationInputException;
import com.irrigation.domain.calculation.exception.MethodologyNotAvailableException;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.CropStage;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;
import com.irrigation.domain.model.valueobjects.GeographicLocation;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;
import com.irrigation.support.FakeCalculationMethod;
import com.irrigation.support.RangeSpec;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.PropertyDefaults;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Propriedades de correção 1 a 8 do design (value objects, agregados e motor de cálculo).
 */
@PropertyDefaults(tries = 200)
class DomainPropertiesTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // =====================================================================================
    // Propriedade 1: value objects numéricos aplicam seus intervalos válidos
    // =====================================================================================

    record Sample(RangeSpec spec, BigDecimal value) {
    }

    @Provide
    Arbitrary<RangeSpec> specs() {
        return Arbitraries.of(RangeSpec.ALL);
    }

    @Provide
    Arbitrary<Sample> samples() {
        return Arbitraries.of(RangeSpec.ALL).flatMap(spec -> {
            BigDecimal low = spec.min().subtract(BigDecimal.ONE);
            BigDecimal high = (spec.max() != null ? spec.max() : spec.min().add(BigDecimal.valueOf(1000)))
                    .add(BigDecimal.ONE);
            return Arbitraries.bigDecimals().between(low, high).ofScale(2).map(v -> new Sample(spec, v));
        });
    }

    private static boolean builds(RangeSpec spec, BigDecimal value) {
        try {
            spec.factory().apply(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Feature: irrigation-management-backend, Property 1: value objects numéricos aplicam seus intervalos
    @Property
    void numericValueObjectsAcceptExactlyTheirValidRange(@ForAll("samples") Sample sample) {
        assertThat(builds(sample.spec(), sample.value()))
                .as("%s com valor %s", sample.spec(), sample.value())
                .isEqualTo(sample.spec().accepts(sample.value()));
    }

    // Feature: irrigation-management-backend, Property 1: limites exatos
    @Property
    void numericValueObjectsHonorTheExactBoundaries(@ForAll("specs") RangeSpec spec) {
        assertThat(builds(spec, spec.lowestValid())).isTrue();
        assertThat(builds(spec, spec.highestBelow())).isFalse();
        if (spec.max() != null) {
            assertThat(builds(spec, spec.max())).isTrue();
            assertThat(builds(spec, spec.lowestAbove())).isFalse();
        }
    }

    // Feature: irrigation-management-backend, Property 1: GeographicLocation
    @Property
    void geographicLocationAcceptsExactlyValidCoordinates(
            @ForAll("latitudes") BigDecimal latitude, @ForAll("longitudes") BigDecimal longitude) {
        boolean expected = latitude.abs().compareTo(bd("90")) <= 0 && longitude.abs().compareTo(bd("180")) <= 0;
        boolean built;
        try {
            new GeographicLocation(latitude, longitude);
            built = true;
        } catch (IllegalArgumentException e) {
            built = false;
        }
        assertThat(built).isEqualTo(expected);
    }

    @Provide
    Arbitrary<BigDecimal> latitudes() {
        return Arbitraries.bigDecimals().between(bd("-100"), bd("100")).ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> longitudes() {
        return Arbitraries.bigDecimals().between(bd("-200"), bd("200")).ofScale(2);
    }

    // Feature: irrigation-management-backend, Property 1: CalculationResult
    @Property
    void calculationResultAcceptsExactlyValidComponents(
            @ForAll("depths") BigDecimal depth, @ForAll("volumes") BigDecimal volume, @ForAll("times") BigDecimal time) {
        boolean expected = depth.signum() >= 0 && depth.compareTo(bd("500")) <= 0
                && volume.signum() >= 0 && volume.compareTo(bd("999999.99")) <= 0
                && time.signum() >= 0 && time.compareTo(bd("999.99")) <= 0;
        boolean built;
        try {
            new CalculationResult(depth, volume, time);
            built = true;
        } catch (IllegalArgumentException e) {
            built = false;
        }
        assertThat(built).isEqualTo(expected);
    }

    @Provide
    Arbitrary<BigDecimal> depths() {
        return Arbitraries.bigDecimals().between(bd("-10"), bd("510")).ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> volumes() {
        return Arbitraries.bigDecimals().between(bd("-10"), bd("1000010")).ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> times() {
        return Arbitraries.bigDecimals().between(bd("-10"), bd("1010")).ofScale(2);
    }

    // =====================================================================================
    // Propriedade 2: textos aplicam presença e tamanho máximo
    // =====================================================================================

    @Provide
    Arbitrary<String> texts() {
        Arbitrary<String> random = Arbitraries.strings().withChars('a', 'b', ' ').ofMaxLength(300);
        Arbitrary<String> byLength = Arbitraries.integers().between(0, 300).map(n -> "a".repeat(n));
        Arbitrary<String> blanks = Arbitraries.integers().between(0, 300).map(n -> " ".repeat(n));
        return Arbitraries.oneOf(random, byLength, blanks);
    }

    private static boolean validText(String text, int maxLength) {
        return !text.isBlank() && text.length() <= maxLength;
    }

    // Feature: irrigation-management-backend, Property 2: nome da área
    @Property
    void areaNameRequiresNonBlankUpTo255(@ForAll("texts") String name) {
        boolean built;
        try {
            TestData.area(UUID.randomUUID(), name, "10.00");
            built = true;
        } catch (IllegalArgumentException e) {
            built = false;
        }
        assertThat(built).isEqualTo(validText(name, 255));
    }

    // Feature: irrigation-management-backend, Property 2: nome da cultura
    @Property
    void cropNameRequiresNonBlankUpTo255(@ForAll("texts") String name) {
        boolean built;
        try {
            new Crop(UUID.randomUUID(), name, null, null, List.of(new CropStage("E", new Kc(BigDecimal.ONE), 1)));
            built = true;
        } catch (IllegalArgumentException e) {
            built = false;
        }
        assertThat(built).isEqualTo(validText(name, 255));
    }

    // Feature: irrigation-management-backend, Property 2: nome do estágio
    @Property
    void stageNameRequiresNonBlankUpTo100(@ForAll("texts") String name) {
        boolean built;
        try {
            new CropStage(name, new Kc(BigDecimal.ONE), 1);
            built = true;
        } catch (IllegalArgumentException e) {
            built = false;
        }
        assertThat(built).isEqualTo(validText(name, 100));
    }

    // =====================================================================================
    // Propriedade 3: Crop aplica quantidade de estágios e ordem positiva única
    // =====================================================================================

    @Provide
    Arbitrary<List<Integer>> stageOrders() {
        Arbitrary<List<Integer>> unique = Arbitraries.integers().between(1, 30).list()
                .uniqueElements().ofMaxSize(22);
        Arbitrary<List<Integer>> arbitrary = Arbitraries.integers().between(-1, 25).list().ofMaxSize(22);
        return Arbitraries.oneOf(unique, arbitrary);
    }

    // Feature: irrigation-management-backend, Property 3: estágios de Crop
    @Property
    void cropAcceptsOneToTwentyStagesWithUniquePositiveOrder(@ForAll("stageOrders") List<Integer> orders) {
        boolean expected = !orders.isEmpty() && orders.size() <= 20
                && orders.stream().allMatch(o -> o > 0)
                && new HashSet<>(orders).size() == orders.size();
        boolean built;
        try {
            List<CropStage> stages = orders.stream()
                    .map(o -> new CropStage("E" + o, new Kc(BigDecimal.ONE), o)).toList();
            new Crop(UUID.randomUUID(), "Milho", null, null, stages);
            built = true;
        } catch (IllegalArgumentException e) {
            built = false;
        }
        assertThat(built).isEqualTo(expected);
    }

    // =====================================================================================
    // Propriedade 4: capacidade de campo estritamente maior que o ponto de murcha
    // =====================================================================================

    @Provide
    Arbitrary<BigDecimal> fractions() {
        return Arbitraries.bigDecimals().between(BigDecimal.ZERO, BigDecimal.ONE).ofScale(2);
    }

    private static SoilProfile soil(BigDecimal fieldCapacity, BigDecimal wiltingPoint, MeasurementUnit[] units) {
        return new SoilProfile(UUID.randomUUID(), "Franco",
                new VolumetricWaterContent(fieldCapacity), new VolumetricWaterContent(wiltingPoint),
                new BulkDensity(bd("1.4")), new RootDepth(bd("500")), new AvailableWater(bd("80")),
                units[0], units[1], units[2], units[3]);
    }

    private static MeasurementUnit[] allUnits() {
        return new MeasurementUnit[] {
                MeasurementUnit.VOLUMETRIC_WATER_CONTENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS};
    }

    // Feature: irrigation-management-backend, Property 4: CC > PMP
    @Property
    void fieldCapacityMustExceedWiltingPoint(
            @ForAll("fractions") BigDecimal fieldCapacity, @ForAll("fractions") BigDecimal wiltingPoint) {
        boolean built;
        try {
            soil(fieldCapacity, wiltingPoint, allUnits());
            built = true;
        } catch (IllegalArgumentException e) {
            built = false;
        }
        assertThat(built).isEqualTo(fieldCapacity.compareTo(wiltingPoint) > 0);
    }

    // =====================================================================================
    // Propriedade 5: todo parâmetro de solo carrega uma unidade explícita
    // =====================================================================================

    // Feature: irrigation-management-backend, Property 5: unidades explícitas
    @Property
    void soilProfileRejectsAnyMissingUnit(@ForAll @IntRange(min = 0, max = 3) int missingIndex) {
        MeasurementUnit[] units = allUnits();
        units[missingIndex] = null;
        assertThatThrownBy(() -> soil(bd("0.35"), bd("0.15"), units)).isInstanceOf(NullPointerException.class);
    }

    // Feature: irrigation-management-backend, Property 5: perfil completo é aceito
    @Property
    void soilProfileWithAllUnitsKeepsThem(
            @ForAll("units") MeasurementUnit a, @ForAll("units") MeasurementUnit b,
            @ForAll("units") MeasurementUnit c, @ForAll("units") MeasurementUnit d) {
        SoilProfile profile = soil(bd("0.35"), bd("0.15"), new MeasurementUnit[] {a, b, c, d});
        assertThat(profile.getSoilWaterUnit()).isEqualTo(a);
        assertThat(profile.getBulkDensityUnit()).isEqualTo(b);
        assertThat(profile.getRootDepthUnit()).isEqualTo(c);
        assertThat(profile.getAvailableWaterUnit()).isEqualTo(d);
    }

    @Provide
    Arbitrary<MeasurementUnit> units() {
        return Arbitraries.of(MeasurementUnit.class);
    }

    // =====================================================================================
    // Propriedade 6: motor rejeita entradas inválidas e não executa o cálculo
    // =====================================================================================

    private static final String[] PARAM_NAMES = {
            "ETo", "Kc", "ETc", "precipitação efetiva", "umidade atual do solo",
            "capacidade de armazenamento de água", "eficiência do sistema de irrigação",
            "lâmina líquida de irrigação", "lâmina bruta de irrigação", "área irrigada",
            "vazão do sistema", "tempo de irrigação"};

    /** Faixas [min, max] na ordem do construtor de CalculationInput; Kc (1) e eficiência (6) são value objects. */
    private static final String[][] PARAM_RANGES = {
            {"0", "15"}, null, {"0", "15"}, {"0", "500"}, {"0", "100"}, {"0", "500"}, null,
            {"0", "500"}, {"0", "500"}, {"0.01", "99999.99"}, {"0.01", "9999.99"}, {"0", "999.99"}};

    /** Modo por parâmetro: 0 = válido, 1 = ausente, 2 = abaixo da faixa, 3 = acima da faixa. */
    @Provide
    Arbitrary<List<Integer>> modes() {
        return Arbitraries.integers().between(0, 3).list().ofSize(12);
    }

    private static BigDecimal value(int slot, int mode) {
        String[] range = PARAM_RANGES[slot];
        BigDecimal min = bd(range[0]);
        BigDecimal max = bd(range[1]);
        return switch (mode) {
            case 0 -> min.add(max).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            case 1 -> null;
            case 2 -> min.subtract(bd("0.01"));
            default -> max.add(bd("0.01"));
        };
    }

    private static CalculationInput inputFor(List<Integer> modes) {
        Kc kc = modes.get(1) == 0 ? new Kc(bd("1.0")) : null;
        IrrigationEfficiency efficiency = modes.get(6) == 0 ? new IrrigationEfficiency(bd("0.8")) : null;
        return new CalculationInput(
                value(0, modes.get(0)), kc, value(2, modes.get(2)), value(3, modes.get(3)), value(4, modes.get(4)),
                value(5, modes.get(5)), efficiency, value(7, modes.get(7)), value(8, modes.get(8)),
                value(9, modes.get(9)), value(10, modes.get(10)), value(11, modes.get(11)));
    }

    // Feature: irrigation-management-backend, Property 6: entradas inválidas não executam o cálculo
    @Property
    void engineReportsExactlyTheInvalidParametersAndDoesNotExecute(@ForAll("modes") List<Integer> modes) {
        Set<String> expectedInvalid = new HashSet<>();
        for (int i = 0; i < modes.size(); i++) {
            if (modes.get(i) != 0) {
                expectedInvalid.add(PARAM_NAMES[i]);
            }
        }
        FakeCalculationMethod method = new FakeCalculationMethod("m");
        CalculationEngine engine = new CalculationEngine();
        engine.register(method);
        CalculationInput input = inputFor(modes);

        if (expectedInvalid.isEmpty()) {
            engine.calculate(new MethodologyIdentifier("m"), input);
            assertThat(method.calls()).isEqualTo(1);
        } else {
            assertThatThrownBy(() -> engine.calculate(new MethodologyIdentifier("m"), input))
                    .isInstanceOfSatisfying(InvalidCalculationInputException.class, e ->
                            assertThat(e.getInvalidParameters()).containsExactlyInAnyOrderElementsOf(expectedInvalid));
            assertThat(method.calls()).isZero();
        }
    }

    // =====================================================================================
    // Propriedade 7: delegação às metodologias registradas e rejeição das demais
    // =====================================================================================

    @Provide
    Arbitrary<List<String>> registeredIds() {
        return Arbitraries.of("a", "b", "c", "d").list().uniqueElements().ofMaxSize(4);
    }

    @Provide
    Arbitrary<String> requestedIds() {
        return Arbitraries.of("a", "b", "c", "d", "e");
    }

    private static CalculationResult resultFor(String id, int salt) {
        return new CalculationResult(BigDecimal.valueOf(id.charAt(0) + salt), BigDecimal.ONE, BigDecimal.ONE);
    }

    // Feature: irrigation-management-backend, Property 7: delegação e metodologia indisponível
    @Property
    void engineDelegatesToTheRegisteredMethodOrRejects(
            @ForAll("registeredIds") List<String> registered, @ForAll("requestedIds") String requested) {
        CalculationEngine engine = new CalculationEngine();
        Map<String, FakeCalculationMethod> methods = new LinkedHashMap<>();
        for (String id : registered) {
            FakeCalculationMethod method = new FakeCalculationMethod(id, "1.0", resultFor(id, 0));
            methods.put(id, method);
            engine.register(method);
        }
        MethodologyIdentifier requestedId = new MethodologyIdentifier(requested);

        if (registered.contains(requested)) {
            CalculationResult result = engine.calculate(requestedId, TestData.calculationInput());
            assertThat(result).isEqualTo(resultFor(requested, 0));
            methods.forEach((id, m) -> assertThat(m.calls()).isEqualTo(id.equals(requested) ? 1 : 0));
        } else {
            assertThatThrownBy(() -> engine.calculate(requestedId, TestData.calculationInput()))
                    .isInstanceOf(MethodologyNotAvailableException.class);
            methods.values().forEach(m -> assertThat(m.calls()).isZero());
        }
    }

    // =====================================================================================
    // Propriedade 8: registro nunca altera registros existentes
    // =====================================================================================

    @Provide
    Arbitrary<List<String>> registrations() {
        return Arbitraries.of("a", "b", "c", "d").list().ofMaxSize(10);
    }

    // Feature: irrigation-management-backend, Property 8: registro preserva metodologias existentes
    @Property
    void registrationNeverChangesExistingRegistrations(@ForAll("registrations") List<String> ids) {
        CalculationEngine engine = new CalculationEngine();
        Map<String, FakeCalculationMethod> originals = new LinkedHashMap<>();
        List<FakeCalculationMethod> rejectedDuplicates = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            FakeCalculationMethod candidate = new FakeCalculationMethod(id, "v" + i, resultFor(id, i + 1));
            if (originals.containsKey(id)) {
                assertThatThrownBy(() -> engine.register(candidate)).isInstanceOf(DuplicateMethodologyException.class);
                rejectedDuplicates.add(candidate);
            } else {
                engine.register(candidate);
                originals.put(id, candidate);
            }
        }

        originals.forEach((id, original) -> {
            CalculationResult result = engine.calculate(new MethodologyIdentifier(id), TestData.calculationInput());
            assertThat(result).isEqualTo(original.calculate(TestData.calculationInput()));
        });
        rejectedDuplicates.forEach(duplicate -> assertThat(duplicate.calls()).isZero());
    }
}
