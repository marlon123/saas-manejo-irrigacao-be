package com.irrigation.application.service;

import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.scenarioDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Testes unitários de {@link WaterBalanceQueryService} (Req 13.6 e 14), sobre o Cenário A calculado
 * (dias 1 a 5; os dias 6 e 7 ficam sem cálculo).
 */
class WaterBalanceQueryServiceTest {

    private WaterBalanceServiceHarness h;

    @BeforeEach
    void setUp() {
        h = WaterBalanceServiceHarness.scenarioA(7);
        h.calculateDays(5);
    }

    private static List<String> violatedFields(Throwable t) {
        assertThat(t).isInstanceOf(ValidationFailedException.class);
        return ((ValidationFailedException) t).getViolations().stream().map(FieldViolation::field).toList();
    }

    // ------------------------------------------------------------ Período

    @Test
    @DisplayName("Req 14.1: período inclusivo nas duas pontas, em ordem cronológica crescente")
    void periodIsInclusiveAndAscending() {
        PagedResult<WaterBalanceOutput> page =
                h.query.findByPeriod(h.areaId, scenarioDate(2), scenarioDate(4), null, null);

        assertThat(page.content()).extracting(WaterBalanceOutput::date)
                .containsExactly(scenarioDate(2), scenarioDate(3), scenarioDate(4));
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("Req 14.1: período com uma só data (de = até) devolve esse dia")
    void singleDayPeriod() {
        PagedResult<WaterBalanceOutput> page =
                h.query.findByPeriod(h.areaId, scenarioDate(3), scenarioDate(3), 0, 10);

        assertThat(page.content()).extracting(WaterBalanceOutput::date).containsExactly(scenarioDate(3));
    }

    @Test
    @DisplayName("Req 14.1: pontas abertas (nulas) cobrem toda a série")
    void openEndedPeriodReturnsEverything() {
        PagedResult<WaterBalanceOutput> page = h.query.findByPeriod(h.areaId, null, null, null, null);

        assertThat(page.content()).hasSize(5);
        assertThat(page.totalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("Req 14.1: paginação com página e tamanho explícitos")
    void pagination() {
        PagedResult<WaterBalanceOutput> second =
                h.query.findByPeriod(h.areaId, scenarioDate(1), scenarioDate(7), 1, 2);

        assertThat(second.content()).extracting(WaterBalanceOutput::date)
                .containsExactly(scenarioDate(3), scenarioDate(4));
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.totalElements()).isEqualTo(5);
        assertThat(second.totalPages()).isEqualTo(3);

        PagedResult<WaterBalanceOutput> last =
                h.query.findByPeriod(h.areaId, scenarioDate(1), scenarioDate(7), 2, 2);
        assertThat(last.content()).extracting(WaterBalanceOutput::date).containsExactly(scenarioDate(5));
    }

    @Test
    @DisplayName("Req 14.5: período sem registros em área existente é página vazia com total 0")
    void emptyPeriodIsAnEmptyPage() {
        PagedResult<WaterBalanceOutput> page =
                h.query.findByPeriod(h.areaId, scenarioDate(6), scenarioDate(7), null, null);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    @DisplayName("Req 14.1 e 13.6: a consulta por período devolve só as revisões correntes")
    void periodReturnsOnlyCurrentRevisions() {
        h.setWeather(2, "7.50", "0");
        h.reprocessFrom(2);

        PagedResult<WaterBalanceOutput> page = h.query.findByPeriod(h.areaId, null, null, null, null);

        assertThat(page.content()).hasSize(5);
        assertThat(page.content()).extracting(o -> o.control().current()).containsOnly(true);
        assertThat(page.content()).extracting(o -> o.control().revision()).containsExactly(1, 2, 2, 2, 2);
        assertThat(page.totalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("Req 14.6: valores em mm saem com a política de apresentação (2 casas) e tempo em 4 casas")
    void periodAppliesPresentationRounding() {
        WaterBalanceOutput day2 = h.query.findByPeriod(h.areaId, scenarioDate(2), scenarioDate(2), null, null)
                .content().get(0);

        assertThat(day2.results().afdFinalMm().scale()).isEqualTo(2);
        assertThat(day2.results().operatingTime().hours()).isEqualByComparingTo("0.8333");
        assertThat(day2.results().operatingTime().hours().scale()).isEqualTo(4);
        assertThat(day2.results().operatingTime().minutes()).isEqualTo(50);
    }

    // ------------------------------------------------------------ Período inválido

    @Test
    @DisplayName("Req 14.4: data inicial posterior à final é 400 no campo from, sem registros")
    void fromAfterToIsBadRequest() {
        Throwable t = catchThrowable(
                () -> h.query.findByPeriod(h.areaId, scenarioDate(4), scenarioDate(2), null, null));

        assertThat(violatedFields(t)).containsExactly("from");
    }

    @Test
    @DisplayName("Req 14.8: página negativa é 400 no campo page, sem ajuste automático")
    void negativePageIsBadRequest() {
        Throwable t = catchThrowable(() -> h.query.findByPeriod(h.areaId, null, null, -1, 10));

        assertThat(violatedFields(t)).containsExactly("page");
    }

    @Test
    @DisplayName("Req 14.8: tamanho 0 e tamanho 101 são 400 no campo size, sem ajuste automático")
    void sizeOutOfRangeIsBadRequest() {
        assertThat(violatedFields(catchThrowable(() -> h.query.findByPeriod(h.areaId, null, null, 0, 0))))
                .containsExactly("size");
        assertThat(violatedFields(catchThrowable(() -> h.query.findByPeriod(h.areaId, null, null, 0, 101))))
                .containsExactly("size");
        // limites válidos
        assertThat(h.query.findByPeriod(h.areaId, null, null, 0, 1).content()).hasSize(1);
        assertThat(h.query.findByPeriod(h.areaId, null, null, 0, 100).size()).isEqualTo(100);
    }

    @Test
    @DisplayName("Req 14.8: todas as violações de período e paginação são acumuladas numa só resposta")
    void allViolationsAreAccumulated() {
        Throwable t = catchThrowable(
                () -> h.query.findByPeriod(h.areaId, scenarioDate(4), scenarioDate(2), -1, 500));

        assertThat(violatedFields(t)).containsExactlyInAnyOrder("from", "page", "size");
    }

    @Test
    @DisplayName("Req 14.7: área inexistente é 404, não página vazia")
    void unknownAreaOnPeriodIsNotFound() {
        assertThatThrownBy(() -> h.query.findByPeriod(UUID.randomUUID(), null, null, null, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Req 14.8: a validação (400) vem antes da existência da área (404)")
    void validationComesBeforeAreaLookup() {
        assertThatThrownBy(() -> h.query.findByPeriod(UUID.randomUUID(), scenarioDate(4), scenarioDate(2), null, null))
                .isInstanceOf(ValidationFailedException.class);
    }

    // ------------------------------------------------------------ Por data

    @Test
    @DisplayName("Req 14.2: consulta por data devolve entradas, resultados e controle da revisão corrente")
    void findByDateReturnsTheCurrentBalance() {
        WaterBalanceOutput out = h.query.findByDate(h.areaId, scenarioDate(4));

        assertThat(out.id()).isEqualTo(h.current(4).id());
        assertThat(out.areaId()).isEqualTo(h.areaId);
        assertThat(out.date()).isEqualTo(scenarioDate(4));
        assertThat(out.cycleDay()).isEqualTo(1);
        assertThat(out.inputs().etpMm()).isEqualByComparingTo("6.25");
        assertThat(out.results().afdInitialMm()).isEqualByComparingTo("28");
        assertThat(out.results().afdFinalMm()).isEqualByComparingTo("23");
        assertThat(out.results().idealIrrigationMm()).isEqualByComparingTo("5");
        assertThat(out.results().irrigationRecommended()).isTrue();
        assertThat(out.control().revision()).isEqualTo(1);
        assertThat(out.control().current()).isTrue();
    }

    @Test
    @DisplayName("Req 14.2: após reprocessar, a consulta por data devolve a nova revisão")
    void findByDateReturnsTheNewRevisionAfterReprocess() {
        h.setWeather(2, "7.50", "0");
        h.reprocessFrom(2);

        WaterBalanceOutput out = h.query.findByDate(h.areaId, scenarioDate(2));

        assertThat(out.control().revision()).isEqualTo(2);
        assertThat(out.results().afdFinalMm()).isEqualByComparingTo("26");
    }

    @Test
    @DisplayName("Req 14.3: data sem cálculo é 404")
    void findByDateWithoutCalculationIsNotFound() {
        assertThatThrownBy(() -> h.query.findByDate(h.areaId, scenarioDate(6)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(scenarioDate(6).toString());
    }

    @Test
    @DisplayName("Req 14.7: área inexistente na consulta por data é 404")
    void findByDateUnknownAreaIsNotFound() {
        assertThatThrownBy(() -> h.query.findByDate(UUID.randomUUID(), scenarioDate(1)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Req 14.8: data ausente na consulta por data é 400 no campo date")
    void findByDateNullDateIsBadRequest() {
        Throwable t = catchThrowable(() -> h.query.findByDate(h.areaId, null));

        assertThat(violatedFields(t)).containsExactly("date");
    }

    // ------------------------------------------------------------ Revisões

    @Test
    @DisplayName("Req 13.6: todas as revisões da data, da mais recente para a mais antiga")
    void revisionsAreOrderedFromNewestToOldest() {
        h.setWeather(2, "7.50", "0");
        h.reprocessFrom(2);
        h.setWeather(2, "5.00", "0");
        h.reprocessFrom(2);

        List<WaterBalanceOutput> revisions = h.query.findRevisions(h.areaId, scenarioDate(2));

        assertThat(revisions).extracting(o -> o.control().revision()).containsExactly(3, 2, 1);
        assertThat(revisions).extracting(o -> o.control().current()).containsExactly(true, false, false);
        assertThat(revisions.get(1).results().afdFinalMm()).isEqualByComparingTo("26");
        assertThat(revisions.get(2).results().afdFinalMm()).isEqualByComparingTo("27");
        assertThat(revisions.get(0).results().afdFinalMm()).isEqualByComparingTo("28");
    }

    @Test
    @DisplayName("Req 13.6: data com uma só revisão devolve lista com um elemento")
    void revisionsOfUnreprocessedDate() {
        List<WaterBalanceOutput> revisions = h.query.findRevisions(h.areaId, scenarioDate(1));

        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0).control().revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("Req 13.6: data nunca calculada é 404 também nas revisões")
    void revisionsOfNeverCalculatedDateIsNotFound() {
        LocalDate never = scenarioDate(7);

        assertThatThrownBy(() -> h.query.findRevisions(h.areaId, never))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Req 14.7: área inexistente nas revisões é 404; data ausente é 400")
    void revisionsUnknownAreaAndNullDate() {
        assertThatThrownBy(() -> h.query.findRevisions(UUID.randomUUID(), scenarioDate(1)))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(violatedFields(catchThrowable(() -> h.query.findRevisions(h.areaId, null))))
                .containsExactly("date");
    }
}
