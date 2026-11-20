package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.support.FakeAreaOperationLock;
import com.irrigation.support.InMemoryDailyWaterBalanceRepository;
import com.irrigation.support.TestData;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Propriedade 17: lacuna no histórico.
 *
 * <p>Para um plano em que alguns dos dias entre o início do plano e o dia anterior à data pedida já têm
 * balanço corrente e outros não, o cálculo é recusado com a regra de negócio (422) citando a PRIMEIRA data
 * sem cálculo, sem gravar nada e sem usar um valor arbitrário de AFDi. Sem lacuna, o cálculo ocorre e usa a
 * AFDf do dia anterior (o resultado é idêntico ao da cadeia calculada em ordem). O primeiro dia do plano
 * não exige histórico.
 *
 * <p>As lacunas são montadas calculando a cadeia completa do Cenário A em um conjunto de repositórios e
 * copiando só os dias escolhidos para um repositório novo, usado pelo serviço sob teste. Assim os balanços
 * existentes são genuínos, sem alterar fixtures nem o harness.
 *
 * <p>**Validates: Requirements 7.8, 8.4**
 */
class HistoryGapPropertyTest {

    // Feature: irrigation-water-balance-module, Property 17: Lacuna no histórico
    @Property(tries = 150)
    void rejectsFirstMissingDateOrCalculatesWithoutGap(
            @ForAll @IntRange(min = 1, max = 7) int targetDay,
            @ForAll @Size(6) List<Boolean> calculated) {

        // Cadeia completa dos dias 1..targetDay, calculada em ordem (referência)
        WaterBalanceServiceHarness reference = WaterBalanceServiceHarness.scenarioA(7);
        reference.calculateDays(targetDay);

        // Repositório do serviço sob teste: só os dias anteriores escolhidos
        InMemoryDailyWaterBalanceRepository balances = new InMemoryDailyWaterBalanceRepository();
        Integer firstMissing = null;
        for (int d = 1; d < targetDay; d++) {
            if (calculated.get(d - 1)) {
                balances.insert(reference.current(d));
            } else if (firstMissing == null) {
                firstMissing = d;
            }
        }
        WaterBalanceService service = new WaterBalanceService(reference.areas, reference.plans, reference.soils,
                reference.crops, reference.weather, reference.irrigation, balances, new FakeAreaOperationLock(),
                TestData.fixedClock());

        List<DailyWaterBalance> before = balances.snapshot();
        LocalDate date = TestData.scenarioDate(targetDay);
        CalculateWaterBalanceInput input = new CalculateWaterBalanceInput(date,
                TestData.SCENARIO_A_CYCLE_DAYS.get(targetDay - 1));

        if (firstMissing != null) {
            String missingDate = TestData.scenarioDate(firstMissing).toString();
            assertThatThrownBy(() -> service.calculate(reference.areaId, input))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining(missingDate);
            assertThat(balances.snapshot()).isEqualTo(before);
            assertThat(balances.findCurrent(reference.areaId, date)).isEmpty();
        } else {
            WaterBalanceOutput output = service.calculate(reference.areaId, input);
            assertThat(output.date()).isEqualTo(date);
            assertThat(balances.count()).isEqualTo(before.size() + 1);
            DailyWaterBalance saved = balances.findCurrent(reference.areaId, date).orElseThrow();
            // AFDi vem da AFDf do dia anterior, igual à cadeia de referência
            assertThat(saved.inputs()).isEqualTo(reference.current(targetDay).inputs());
            assertThat(saved.result()).isEqualTo(reference.current(targetDay).result());
        }
    }
}
