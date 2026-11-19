package com.irrigation.application.service;

import com.irrigation.application.dto.ManagementPlanInput;
import com.irrigation.application.dto.ManagementPlanOutput;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryManagementPlanRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.area;
import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriedade 23: um plano ativo por área.
 *
 * <p>Sequências arbitrárias de criações, encerramentos e reaberturas de planos para várias áreas nunca deixam
 * mais de um plano sem data final por área nem dois períodos sobrepostos. Operações recusadas lançam
 * {@link BusinessRuleException} e não alteram os planos da área. A consulta vem em ordem decrescente de início.
 *
 * <p>Cultura e solo são stubs que aceitam qualquer identificador: o foco aqui é a regra de períodos.
 *
 * <p>**Validates: Requirements 4.7, 4.8, 4.11**
 */
class SingleActivePlanPropertyTest {

    private static final LocalDate BASE = LocalDate.of(2026, 1, 1);
    private static final int AREAS = 3;

    /** Operação sobre a área de índice {@code area}; {@code days} é o início (criar) ou o fim (encerrar). */
    private sealed interface Op permits Create, Close {
    }

    private record Create(int area, int startDay, Integer durationDays) implements Op {
    }

    /** Atualiza o plano de índice {@code pick} da área; {@code endDay} nulo reabre o plano. */
    private record Close(int area, int pick, Integer endOffsetDays) implements Op {
    }

    private static final class Fixture {
        final InMemoryManagementPlanRepository plans = new InMemoryManagementPlanRepository();
        final InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        final List<UUID> areaIds = new ArrayList<>();
        final UUID cropId = UUID.randomUUID();
        final UUID soilId = UUID.randomUUID();
        final ManagementPlanService service;

        Fixture() {
            for (int i = 0; i < AREAS; i++) {
                UUID id = UUID.randomUUID();
                areas.save(area(id, "Talhão " + i, "10.50"));
                areaIds.add(id);
            }
            CropRepository crops = mock(CropRepository.class);
            SoilProfileRepository soils = mock(SoilProfileRepository.class);
            when(crops.existsById(any())).thenReturn(true);
            when(soils.existsById(any())).thenReturn(true);
            service = new ManagementPlanService(plans, areas, crops, soils);
        }

        ManagementPlanInput input(int startDay, LocalDate end) {
            return new ManagementPlanInput(cropId, soilId, BASE.plusDays(startDay), end, bd("0.5"), bd("10"));
        }
    }

    @Provide
    Arbitrary<List<Op>> operations() {
        Arbitrary<Op> create = Arbitraries.integers().between(0, AREAS - 1).flatMap(area ->
                Arbitraries.integers().between(0, 120).flatMap(start ->
                        Arbitraries.integers().between(0, 40).injectNull(0.4)
                                .map(duration -> (Op) new Create(area, start, duration))));
        Arbitrary<Op> close = Arbitraries.integers().between(0, AREAS - 1).flatMap(area ->
                Arbitraries.integers().between(0, 5).flatMap(pick ->
                        Arbitraries.integers().between(0, 160).injectNull(0.2)
                                .map(end -> (Op) new Close(area, pick, end))));
        return Arbitraries.oneOf(create, close).list().ofMinSize(1).ofMaxSize(25);
    }

    // Feature: irrigation-water-balance-module, Property 23: Um plano ativo por área
    @Property(tries = 200)
    void arbitrarySequencesKeepAtMostOneActivePlanAndNoOverlap(@ForAll("operations") List<Op> ops) {
        Fixture f = new Fixture();

        for (Op op : ops) {
            UUID areaId = f.areaIds.get(op instanceof Create c ? c.area() : ((Close) op).area());
            List<ManagementPlanOutput> before = f.service.listByArea(areaId);

            try {
                apply(f, areaId, before, op);
            } catch (BusinessRuleException refused) {
                // recusa por regra de negócio (422): nada pode ter mudado na área
                assertThat(f.service.listByArea(areaId)).isEqualTo(before);
            }

            for (UUID id : f.areaIds) {
                assertInvariants(f.service.listByArea(id));
            }
        }
    }

    // Feature: irrigation-water-balance-module, Property 23: Um plano ativo por área
    @Property(tries = 100)
    void secondActiveCreationIsRefusedAndExistingActivePlanIsUnchanged(
            @ForAll("startDays") int firstStart,
            @ForAll("startDays") int secondStart,
            @ForAll("startDays") int otherAreaStart) {
        Fixture f = new Fixture();
        UUID areaId = f.areaIds.get(0);
        UUID otherAreaId = f.areaIds.get(1);

        ManagementPlanOutput active = f.service.create(areaId, f.input(firstStart, null));
        List<ManagementPlanOutput> before = f.service.listByArea(areaId);

        assertThatThrownBy(() -> f.service.create(areaId, f.input(secondStart, null)))
                .isInstanceOf(BusinessRuleException.class);

        List<ManagementPlanOutput> after = f.service.listByArea(areaId);
        assertThat(after).isEqualTo(before).containsExactly(active);
        assertThat(after.get(0).endDate()).isNull();

        // outra área continua aceitando o seu próprio plano ativo
        ManagementPlanOutput other = f.service.create(otherAreaId, f.input(otherAreaStart, null));
        assertThat(f.service.listByArea(otherAreaId)).containsExactly(other);
        assertThat(f.service.listByArea(areaId)).containsExactly(active);
    }

    @Provide
    Arbitrary<Integer> startDays() {
        return Arbitraries.integers().between(0, 120);
    }

    private static void apply(Fixture f, UUID areaId, List<ManagementPlanOutput> current, Op op) {
        if (op instanceof Create c) {
            LocalDate end = c.durationDays() == null ? null : BASE.plusDays(c.startDay() + (long) c.durationDays());
            ManagementPlanOutput created = f.service.create(areaId, f.input(c.startDay(), end));
            assertThat(created.areaId()).isEqualTo(areaId);
        } else if (op instanceof Close cl && !current.isEmpty()) {
            ManagementPlanOutput target = current.get(cl.pick() % current.size());
            LocalDate end = cl.endOffsetDays() == null ? null : BASE.plusDays(cl.endOffsetDays());
            if (end != null && end.isBefore(target.startDate())) {
                end = target.startDate();
            }
            f.service.update(areaId, target.id(), f.input(0, end));
        }
    }

    private static void assertInvariants(List<ManagementPlanOutput> plans) {
        // no máximo um plano sem data final
        assertThat(plans.stream().filter(p -> p.endDate() == null).count()).isLessThanOrEqualTo(1);

        // consulta em ordem decrescente de início
        assertThat(plans).isSortedAccordingTo(Comparator.comparing(ManagementPlanOutput::startDate).reversed());

        // sem sobreposição de períodos (data final nula = aberto)
        for (int i = 0; i < plans.size(); i++) {
            for (int j = i + 1; j < plans.size(); j++) {
                ManagementPlanOutput a = plans.get(i);
                ManagementPlanOutput b = plans.get(j);
                boolean aStartsBeforeBEnds = b.endDate() == null || !a.startDate().isAfter(b.endDate());
                boolean bStartsBeforeAEnds = a.endDate() == null || !b.startDate().isAfter(a.endDate());
                assertThat(aStartsBeforeBEnds && bStartsBeforeAEnds)
                        .as("planos %s e %s se sobrepõem", a, b)
                        .isFalse();
            }
        }
    }
}
