package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.SoilProfile;
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
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.DailyWaterBalanceRepository;
import com.irrigation.domain.repository.ManagementPlanRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;
import com.irrigation.support.PostgresIntegrationTest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Persistência do balanço hídrico diário contra o PostgreSQL real: round trip com escala 13, consultas,
 * {@code firstMissingDate}, {@code markNotCurrent} em lote, {@code hasBalances}/{@code lastCalculatedDate} do
 * plano e exclusão de cadastros referenciados (Req 12.5, 12.7, 12.8, 12.9, 12.10, 12.11, 13.8, 16.4).
 *
 * <p>Fica no pacote do adaptador para alcançar o {@link ForeignKeyViolationTranslator}, que é privativo do pacote.
 * Os testes não são transacionais: cada chamada de repositório tem a própria transação, como em produção.
 */
class DailyWaterBalancePersistenceIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 1);

    @Autowired
    AreaRepository areas;
    @Autowired
    CropRepository crops;
    @Autowired
    SoilProfileRepository soilProfiles;
    @Autowired
    ManagementPlanRepository plans;
    @Autowired
    DailyWaterBalanceRepository balances;

    /** Área com cultura, solo e plano ativo desde {@link #DAY}. */
    private record Fixture(UUID areaId, UUID cropId, UUID soilId, UUID planId) {
    }

    private Fixture fixture() {
        Area area = areas.save(TestData.area(UUID.randomUUID(), "Talhão " + UUID.randomUUID(), "10.50"));
        Crop crop = crops.save(TestData.crop());
        SoilProfile soil = soilProfiles.save(TestData.soilProfile());
        ManagementPlan plan = plans.save(TestData.managementPlan(area.getId(), crop.getId(), soil.getId(), DAY));
        return new Fixture(area.getId(), crop.getId(), soil.getId(), plan.getId());
    }

    private DailyWaterBalance balance(Fixture f, LocalDate date, int revision, boolean current) {
        return TestData.dailyWaterBalance(f.areaId(), f.cropId(), f.soilId(), f.planId(), date, revision, current);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    /** Balanço de dia posterior ao primeiro, com AFDi e AFDf de 13 casas decimais. */
    private DailyWaterBalance balanceWithScaleThirteen(Fixture f, LocalDate date) {
        BigDecimal afdInitial = new BigDecimal("36.1234567890123");
        BigDecimal afdFinal = new BigDecimal("-27.9876543210987");
        WaterBalanceInputs inputs = new WaterBalanceInputs(
                new EtpDepth(bd("5.250")), new PrecipitationDepth(bd("1.500")),
                new AppliedIrrigationDepth(bd("2.750")),
                new GravimetricWaterContent(bd("30.50")), new GravimetricWaterContent(bd("15.25")),
                new BulkDensity(bd("1.350")), new RootDepth(bd("400.00")), new Kc(bd("0.85")),
                new AvailabilityFactor(bd("0.3333")), new SprinklerRate(bd("12.5000")),
                false, afdInitial);
        WaterBalanceResult result = new WaterBalanceResult(bd("4.0000000000001"), bd("72.0000000000000"),
                afdInitial, afdFinal, afdInitial.subtract(afdFinal), true, null, bd("0.6666666667"));
        return new DailyWaterBalance(UUID.randomUUID(), f.areaId(), date, f.cropId(), f.soilId(), f.planId(),
                new CycleDay(3), inputs, result, TestData.fixedClock().instant(), "water-balance", "1.0", 1, true);
    }

    // ------------------------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------------------------

    @Test
    void roundTripKeepsAfdInitialAndAfdFinalWithThirteenDecimalPlaces() {
        Fixture f = fixture();
        DailyWaterBalance original = balanceWithScaleThirteen(f, DAY);

        DailyWaterBalance returned = balances.insert(original);
        DailyWaterBalance found = balances.findCurrent(f.areaId(), DAY).orElseThrow();

        for (DailyWaterBalance actual : List.of(returned, found)) {
            assertThat(actual.result().afdInitial().compareTo(original.result().afdInitial())).isZero();
            assertThat(actual.result().afdFinal().compareTo(original.result().afdFinal())).isZero();
            assertThat(actual).usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(original);
        }
        // Sem perda nem arredondamento na própria coluna: NUMERIC sem escala guarda as 13 casas
        BigDecimal storedInitial = jdbc.queryForObject("SELECT afd_initial_mm FROM daily_water_balance",
                BigDecimal.class);
        BigDecimal storedFinal = jdbc.queryForObject("SELECT afd_final_mm FROM daily_water_balance",
                BigDecimal.class);
        assertThat(storedInitial).isEqualTo(new BigDecimal("36.1234567890123"));
        assertThat(storedFinal).isEqualTo(new BigDecimal("-27.9876543210987"));
        assertThat(storedFinal.scale()).isEqualTo(13);
    }

    @Test
    void negativeIdealIrrigationAndZeroOperatingTimeRoundTrip() {
        Fixture f = fixture();
        DailyWaterBalance base = balance(f, DAY, 1, true);
        WaterBalanceResult result = new WaterBalanceResult(bd("5"), bd("72"), bd("27"), bd("28"), bd("-1"),
                false, null, BigDecimal.ZERO);
        DailyWaterBalance negative = new DailyWaterBalance(base.id(), base.areaId(), base.date(), base.cropId(),
                base.soilProfileId(), base.managementPlanId(), base.cycleDay(),
                new WaterBalanceInputs(base.inputs().etp(), base.inputs().precipitation(),
                        base.inputs().appliedIrrigation(), base.inputs().fieldCapacity(),
                        base.inputs().permanentWiltingPoint(), base.inputs().bulkDensity(),
                        base.inputs().rootDepth(), base.inputs().kc(), base.inputs().availabilityFactor(),
                        base.inputs().sprinklerRate(), false, bd("27")),
                result, base.calculatedAt(), base.methodologyId(), base.methodologyVersion(), 1, true);

        balances.insert(negative);

        DailyWaterBalance found = balances.findCurrent(f.areaId(), DAY).orElseThrow();
        assertThat(found.result().idealIrrigation()).isEqualByComparingTo("-1");
        assertThat(found.result().operatingTimeHours()).isEqualByComparingTo("0");
        assertThat(found.result().recommended()).isFalse();
    }

    @Test
    void insertAllKeepsOrderAndEveryRowIsReadBackByPeriodAndRevisionQueries() {
        Fixture f = fixture();
        balances.insertAll(List.of(balance(f, DAY, 1, true), balance(f, DAY.plusDays(1), 1, true),
                balance(f, DAY.plusDays(2), 1, true)));

        assertThat(balances.findCurrentByPeriod(f.areaId(), DAY, DAY.plusDays(1), new PageRequest(0, 10)))
                .extracting(DailyWaterBalance::date).containsExactly(DAY, DAY.plusDays(1));
        assertThat(balances.countCurrentByPeriod(f.areaId(), null, null)).isEqualTo(3);
        assertThat(balances.findCurrentFrom(f.areaId(), DAY.plusDays(1)))
                .extracting(DailyWaterBalance::date).containsExactly(DAY.plusDays(1), DAY.plusDays(2));
        assertThat(balances.findRevisions(f.areaId(), DAY)).extracting(DailyWaterBalance::revision)
                .containsExactly(1);
    }

    // ------------------------------------------------------------------------------------
    // Unicidade traduzida pelo adaptador
    // ------------------------------------------------------------------------------------

    @Test
    void secondCurrentRevisionIsTranslatedToConflictException() {
        Fixture f = fixture();
        balances.insert(balance(f, DAY, 1, true));

        assertThatThrownBy(() -> balances.insert(balance(f, DAY, 2, true)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(f.areaId().toString());
        assertThat(count("daily_water_balance")).isEqualTo(1);
    }

    @Test
    void repeatedRevisionNumberIsTranslatedToConflictException() {
        Fixture f = fixture();
        balances.insert(balance(f, DAY, 1, false));

        assertThatThrownBy(() -> balances.insert(balance(f, DAY, 1, false)))
                .isInstanceOf(ConflictException.class);
        assertThat(count("daily_water_balance")).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------
    // firstMissingDate
    // ------------------------------------------------------------------------------------

    @Test
    void firstMissingDateIsEmptyWhenEveryDateInTheRangeHasACurrentBalance() {
        Fixture f = fixture();
        balances.insertAll(List.of(balance(f, DAY, 1, true), balance(f, DAY.plusDays(1), 1, true),
                balance(f, DAY.plusDays(2), 1, true)));

        assertThat(balances.firstMissingDate(f.areaId(), DAY, DAY.plusDays(2))).isEmpty();
        assertThat(balances.firstMissingDate(f.areaId(), DAY, DAY)).isEmpty();
    }

    @Test
    void firstMissingDateReturnsTheEarliestGap() {
        Fixture f = fixture();
        balances.insertAll(List.of(balance(f, DAY, 1, true), balance(f, DAY.plusDays(1), 1, true),
                balance(f, DAY.plusDays(4), 1, true)));

        assertThat(balances.firstMissingDate(f.areaId(), DAY, DAY.plusDays(4))).contains(DAY.plusDays(2));
        assertThat(balances.firstMissingDate(f.areaId(), DAY.plusDays(3), DAY.plusDays(4)))
                .contains(DAY.plusDays(3));
        assertThat(balances.firstMissingDate(f.areaId(), DAY.plusDays(4), DAY.plusDays(6)))
                .contains(DAY.plusDays(5));
    }

    @Test
    void firstMissingDateIgnoresNonCurrentRevisionsAndOtherAreas() {
        Fixture f = fixture();
        Fixture other = fixture();
        balances.insert(balance(f, DAY, 1, true));
        balances.insert(balance(f, DAY.plusDays(1), 1, false));
        balances.insert(balance(f, DAY.plusDays(2), 1, true));
        balances.insert(balance(other, DAY.plusDays(1), 1, true));

        assertThat(balances.firstMissingDate(f.areaId(), DAY, DAY.plusDays(2))).contains(DAY.plusDays(1));
        assertThat(balances.firstMissingDate(UUID.randomUUID(), DAY, DAY)).contains(DAY);
    }

    @Test
    void firstMissingDateIsEmptyForAnInvertedRange() {
        Fixture f = fixture();

        assertThat(balances.firstMissingDate(f.areaId(), DAY.plusDays(3), DAY)).isEmpty();
    }

    // ------------------------------------------------------------------------------------
    // markNotCurrent em lote
    // ------------------------------------------------------------------------------------

    @Test
    void markNotCurrentDemotesEveryCurrentBalanceFromTheDateOnwardsInOneBatch() {
        Fixture f = fixture();
        Fixture other = fixture();
        for (int i = 0; i < 5; i++) {
            balances.insert(balance(f, DAY.plusDays(i), 1, true));
        }
        balances.insert(balance(other, DAY.plusDays(3), 1, true));
        balances.insert(balance(f, DAY.plusDays(1), 2, false));

        int updated = balances.markNotCurrent(f.areaId(), DAY.plusDays(2));

        assertThat(updated).isEqualTo(3);
        assertThat(balances.findCurrentFrom(f.areaId(), DAY)).extracting(DailyWaterBalance::date)
                .containsExactly(DAY, DAY.plusDays(1));
        assertThat(balances.findCurrent(other.areaId(), DAY.plusDays(3))).isPresent();
        // as linhas continuam gravadas, só deixam de ser correntes
        assertThat(count("daily_water_balance")).isEqualTo(7);
        assertThat(balances.findRevisions(f.areaId(), DAY.plusDays(3))).extracting(DailyWaterBalance::current)
                .containsExactly(false);
        assertThat(balances.markNotCurrent(f.areaId(), DAY.plusDays(2))).as("repetir não altera nada").isZero();
    }

    @Test
    void markNotCurrentChangesNothingElseInTheRows() {
        Fixture f = fixture();
        DailyWaterBalance original = balanceWithScaleThirteen(f, DAY);
        balances.insert(original);

        balances.markNotCurrent(f.areaId(), DAY);

        DailyWaterBalance demoted = balances.findRevisions(f.areaId(), DAY).get(0);
        assertThat(demoted.current()).isFalse();
        assertThat(demoted).usingRecursiveComparison()
                .ignoringFields("current")
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(original);
    }

    @Test
    void aNewCurrentRevisionCanBeInsertedAfterTheBatchDemotion() {
        Fixture f = fixture();
        balances.insert(balance(f, DAY, 1, true));

        balances.markNotCurrent(f.areaId(), DAY);
        balances.insert(balance(f, DAY, 2, true));

        assertThat(balances.findCurrent(f.areaId(), DAY).orElseThrow().revision()).isEqualTo(2);
        assertThat(balances.findRevisions(f.areaId(), DAY)).extracting(DailyWaterBalance::revision)
                .containsExactly(2, 1);
    }

    // ------------------------------------------------------------------------------------
    // hasBalances e lastCalculatedDate do plano
    // ------------------------------------------------------------------------------------

    @Test
    void planWithoutBalancesHasNoBalancesAndNoLastCalculatedDate() {
        Fixture f = fixture();

        assertThat(plans.hasBalances(f.planId())).isFalse();
        assertThat(plans.lastCalculatedDate(f.planId())).isEmpty();
    }

    @Test
    void hasBalancesAndLastCalculatedDateReflectOnlyTheOwnPlan() {
        Fixture f = fixture();
        Fixture other = fixture();
        balances.insert(balance(f, DAY, 1, true));
        balances.insert(balance(f, DAY.plusDays(1), 1, true));
        balances.insert(balance(f, DAY.plusDays(6), 1, true));

        assertThat(plans.hasBalances(f.planId())).isTrue();
        assertThat(plans.lastCalculatedDate(f.planId())).contains(DAY.plusDays(6));
        assertThat(plans.hasBalances(other.planId())).isFalse();
        assertThat(plans.lastCalculatedDate(other.planId())).isEmpty();
    }

    @Test
    void lastCalculatedDateUsesCurrentBalancesWhileHasBalancesCountsEveryRevision() {
        Fixture f = fixture();
        balances.insert(balance(f, DAY, 1, true));
        balances.insert(balance(f, DAY.plusDays(1), 1, true));

        balances.markNotCurrent(f.areaId(), DAY.plusDays(1));

        assertThat(plans.lastCalculatedDate(f.planId())).contains(DAY);
        balances.markNotCurrent(f.areaId(), DAY);
        assertThat(plans.hasBalances(f.planId())).as("revisões antigas contam").isTrue();
        assertThat(plans.lastCalculatedDate(f.planId())).as("sem corrente").isEmpty();
    }

    // ------------------------------------------------------------------------------------
    // Exclusão de cadastros referenciados por balanço
    // ------------------------------------------------------------------------------------

    @Test
    void deletingAnAreaWithBalancesIsRefusedAsABusinessRule() {
        Fixture f = fixture();
        balances.insert(balance(f, DAY, 1, true));

        Throwable error = catchThrowable(() -> areas.deleteById(f.areaId()));

        assertThat(error).isInstanceOf(BusinessRuleException.class);
        assertThat(error.getMessage()).contains(f.areaId().toString());
        assertThat(error.getMessage()).containsPattern("balanços hídricos|planos de manejo");
        assertThat(areas.existsById(f.areaId())).isTrue();
        assertThat(count("daily_water_balance")).isEqualTo(1);
    }

    @Test
    void deletingASoilProfileWithBalancesIsRefusedAsABusinessRule() {
        Fixture f = fixture();
        balances.insert(balance(f, DAY, 1, true));

        Throwable error = catchThrowable(() -> soilProfiles.deleteById(f.soilId()));

        assertThat(error).isInstanceOf(BusinessRuleException.class);
        assertThat(error.getMessage()).contains(f.soilId().toString());
        assertThat(error.getMessage()).containsPattern("balanços hídricos|planos de manejo");
        assertThat(soilProfiles.findById(f.soilId())).isPresent();
    }

    @Test
    void deletingACropWithBalancesIsRefusedAsABusinessRule() {
        Fixture f = fixture();
        balances.insert(balance(f, DAY, 1, true));

        Throwable error = catchThrowable(() -> crops.deleteById(f.cropId()));

        assertThat(error).isInstanceOf(BusinessRuleException.class);
        assertThat(error.getMessage()).contains(f.cropId().toString());
        assertThat(error.getMessage()).containsPattern("balanços hídricos|planos de manejo");
        assertThat(crops.findById(f.cropId())).isPresent();
    }

    /**
     * Nenhum serviço exclui planos, então a FK do plano é exercitada com um DELETE direto, cujo erro real
     * do banco passa pelo tradutor. O nome {@code fk_daily_water_balance_plan} deve resultar em "balanços hídricos".
     */
    @Test
    void deletingAPlanWithBalancesIsTranslatedCitingWaterBalances() {
        Fixture f = fixture();
        balances.insert(balance(f, DAY, 1, true));

        Throwable error = catchThrowable(
                () -> jdbc.update("DELETE FROM management_plans WHERE id = ?", f.planId()));

        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
        BusinessRuleException translated = ForeignKeyViolationTranslator.translate(
                (RuntimeException) error, "o plano de manejo", f.planId());
        assertThat(translated).isNotNull();
        assertThat(translated.getMessage()).contains(f.planId().toString()).contains("balanços hídricos");
        assertThat(count("management_plans")).isEqualTo(1);
    }

    @Test
    void referencesToBalancesAreTheOnlyOnesCitedByTheBalanceForeignKeys() {
        // cada FK de daily_water_balance deve conter 'water_balance' e nenhum dos termos verificados antes dele
        List<String> names = jdbc.queryForList("SELECT conname FROM pg_constraint "
                + "WHERE conrelid = 'daily_water_balance'::regclass AND contype = 'f'", String.class);

        assertThat(names).hasSize(4).allSatisfy(name -> {
            assertThat(name).contains("water_balance");
            assertThat(name).doesNotContain("management_plan", "daily_weather", "applied_irrigation");
        });
    }
}
