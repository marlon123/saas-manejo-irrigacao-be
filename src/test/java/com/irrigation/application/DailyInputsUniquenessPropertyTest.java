package com.irrigation.application;

import com.irrigation.application.dto.AppliedIrrigationInput;
import com.irrigation.application.dto.AppliedIrrigationOutput;
import com.irrigation.application.dto.DailyWeatherInput;
import com.irrigation.application.dto.DailyWeatherOutput;
import com.irrigation.application.service.AppliedIrrigationService;
import com.irrigation.application.service.DailyWeatherService;
import com.irrigation.support.InMemoryAppliedIrrigationRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryDailyWeatherRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 14 do módulo de balanço hídrico: um registro por área e data nas entradas diárias
 * (meteorologia e irrigação real). Roda sem Docker, com repositórios em memória.
 */
class DailyInputsUniquenessPropertyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 30);
    private static final LocalDate FIRST_DAY = TODAY.minusDays(9);
    private static final int AREA_COUNT = 3;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

    /** Uma gravação: índice da área, data e valores (ETP, precipitação e IR). */
    record Op(int areaIndex, LocalDate date, BigDecimal etp, BigDecimal precipitation, BigDecimal irrigation) {
    }

    record Period(LocalDate from, LocalDate to) {
    }

    private record Key(UUID areaId, LocalDate date) {
    }

    @Provide
    Arbitrary<List<Op>> operations() {
        Arbitrary<BigDecimal> mm = Arbitraries.integers().between(0, 100_000).map(i -> BigDecimal.valueOf(i, 2));
        Arbitrary<Op> op = Arbitraries.integers().between(0, AREA_COUNT - 1).flatMap(area ->
                Arbitraries.integers().between(0, 9).flatMap(day ->
                        mm.flatMap(etp -> mm.flatMap(p -> mm.map(ir ->
                                new Op(area, FIRST_DAY.plusDays(day), etp, p, ir))))));
        return op.list().ofMinSize(0).ofMaxSize(40);
    }

    @Provide
    Arbitrary<Period> periods() {
        return Arbitraries.integers().between(0, 9).flatMap(a ->
                Arbitraries.integers().between(a, 9).map(b ->
                        new Period(FIRST_DAY.plusDays(a), FIRST_DAY.plusDays(b))));
    }

    // Feature: irrigation-water-balance-module, Property 14: Um registro por área e data nas entradas diárias
    // Valida: Requisitos 5.1, 5.7, 6.1, 6.7
    @Property(tries = 200)
    void upsertsLeaveExactlyOneRowPerAreaAndDateWithTheLastValueAndStableId(
            @ForAll("operations") List<Op> ops, @ForAll("periods") Period period) {
        InMemoryRecommendationRepository recommendations = new InMemoryRecommendationRepository();
        InMemoryAreaRepository areas = new InMemoryAreaRepository(recommendations);
        InMemoryDailyWeatherRepository weatherRepo = new InMemoryDailyWeatherRepository();
        InMemoryAppliedIrrigationRepository irrigationRepo = new InMemoryAppliedIrrigationRepository();
        DailyWeatherService weatherService = new DailyWeatherService(weatherRepo, areas, CLOCK);
        AppliedIrrigationService irrigationService = new AppliedIrrigationService(irrigationRepo, areas, CLOCK);

        List<UUID> areaIds = new ArrayList<>();
        for (int i = 0; i < AREA_COUNT; i++) {
            UUID id = UUID.randomUUID();
            areas.save(TestData.area(id, "Talhão " + i, "10.50"));
            areaIds.add(id);
        }

        Map<Key, Op> last = new HashMap<>();
        Map<Key, UUID> weatherIds = new HashMap<>();
        Map<Key, UUID> irrigationIds = new HashMap<>();

        for (Op op : ops) {
            UUID areaId = areaIds.get(op.areaIndex());
            Key key = new Key(areaId, op.date());
            last.put(key, op);

            DailyWeatherOutput w = weatherService.upsert(areaId,
                    new DailyWeatherInput(op.date(), op.etp(), op.precipitation()));
            AppliedIrrigationOutput ir = irrigationService.upsert(areaId,
                    new AppliedIrrigationInput(op.date(), op.irrigation()));

            // id estável: o da primeira gravação
            assertThat(w.id()).isEqualTo(weatherIds.computeIfAbsent(key, k -> w.id()));
            assertThat(ir.id()).isEqualTo(irrigationIds.computeIfAbsent(key, k -> ir.id()));
        }

        // exatamente uma linha por (área, data)
        assertThat(weatherRepo.count()).isEqualTo(last.size());
        assertThat(irrigationRepo.count()).isEqualTo(last.size());

        // último valor gravado
        for (Map.Entry<Key, Op> e : last.entrySet()) {
            Key key = e.getKey();
            Op op = e.getValue();
            DailyWeatherOutput w = weatherService.findByDate(key.areaId(), key.date());
            assertThat(w.etpMm()).isEqualByComparingTo(op.etp());
            assertThat(w.precipitationMm()).isEqualByComparingTo(op.precipitation());
            assertThat(w.id()).isEqualTo(weatherIds.get(key));
            AppliedIrrigationOutput ir = irrigationService.findByDate(key.areaId(), key.date());
            assertThat(ir.appliedIrrigationMm()).isEqualByComparingTo(op.irrigation());
            assertThat(ir.id()).isEqualTo(irrigationIds.get(key));
        }

        // consulta por período: inclusiva, crescente, sem datas repetidas
        for (UUID areaId : areaIds) {
            List<LocalDate> expectedDates = last.keySet().stream()
                    .filter(k -> k.areaId().equals(areaId))
                    .map(Key::date)
                    .filter(d -> !d.isBefore(period.from()) && !d.isAfter(period.to()))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(weatherService.findByPeriod(areaId, period.from(), period.to()))
                    .extracting(DailyWeatherOutput::date).containsExactlyElementsOf(expectedDates);
            assertThat(irrigationService.findByPeriod(areaId, period.from(), period.to()))
                    .extracting(AppliedIrrigationOutput::date).containsExactlyElementsOf(expectedDates);
        }
    }
}
