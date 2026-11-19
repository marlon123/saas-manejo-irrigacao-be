package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.infrastructure.persistence.entity.AppliedIrrigationEntity;
import com.irrigation.infrastructure.persistence.entity.DailyWeatherEntity;
import com.irrigation.infrastructure.persistence.entity.ManagementPlanEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapeadores de persistência das entradas do balanço hídrico (plano de manejo, meteorologia diária e
 * irrigação real): domínio → entidade → domínio preserva todos os campos. Testes puros, sem banco.
 * Os valores com escala 3 simulam o que o PostgreSQL devolve de colunas NUMERIC(.., 3), por exemplo
 * 12.500, que os value objects de 2 casas aceitam porque zeros à direita não contam.
 */
class WaterBalanceInputsPersistenceMappersTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 1);
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private final ManagementPlanPersistenceMapper planMapper = new ManagementPlanPersistenceMapper();
    private final DailyWeatherPersistenceMapper weatherMapper = new DailyWeatherPersistenceMapper();
    private final AppliedIrrigationPersistenceMapper irrigationMapper = new AppliedIrrigationPersistenceMapper();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ---------- Plano de manejo ----------

    @Test
    void activePlanRoundTripKeepsAllFieldsAndNullEndDate() {
        ManagementPlan plan = new ManagementPlan(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), START, new AvailabilityFactor(bd("0.5500")), new SprinklerRate(bd("12.5")));

        ManagementPlanEntity entity = planMapper.toEntity(plan);
        assertThat(entity.getEndDate()).isNull();
        assertThat(entity.getId()).isEqualTo(plan.getId());
        assertThat(entity.getAreaId()).isEqualTo(plan.getAreaId());
        assertThat(entity.getCropId()).isEqualTo(plan.getCropId());
        assertThat(entity.getSoilProfileId()).isEqualTo(plan.getSoilProfileId());

        ManagementPlan back = planMapper.toDomain(entity);
        assertThat(back.isActive()).isTrue();
        assertSamePlan(back, plan);
    }

    @Test
    void closedPlanRoundTripKeepsEndDate() {
        LocalDate end = LocalDate.of(2026, 6, 30);
        ManagementPlan plan = new ManagementPlan(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), START, end, new AvailabilityFactor(BigDecimal.ONE), new SprinklerRate(bd("500")));

        ManagementPlan back = planMapper.toDomain(planMapper.toEntity(plan));

        assertThat(back.isActive()).isFalse();
        assertThat(back.getEndDate()).isEqualTo(end);
        assertSamePlan(back, plan);
    }

    @Test
    void closedPlanWithSameStartAndEndDateIsAccepted() {
        ManagementPlan plan = new ManagementPlan(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), START, START, new AvailabilityFactor(bd("0.3")), new SprinklerRate(bd("1")));

        assertSamePlan(planMapper.toDomain(planMapper.toEntity(plan)), plan);
    }

    @Test
    void planFromEntityWithDatabaseScaleAcceptsTrailingZeros() {
        ManagementPlanEntity entity = ManagementPlanEntity.newInstance();
        entity.setId(UUID.randomUUID());
        entity.setAreaId(UUID.randomUUID());
        entity.setCropId(UUID.randomUUID());
        entity.setSoilProfileId(UUID.randomUUID());
        entity.setStartDate(START);
        entity.setEndDate(null);
        entity.setAvailabilityFactor(bd("0.5000"));
        entity.setSprinklerRate(bd("12.5000"));

        ManagementPlan plan = planMapper.toDomain(entity);

        assertThat(plan.getAvailabilityFactor().value()).isEqualByComparingTo("0.5");
        assertThat(plan.getSprinklerRate().value()).isEqualByComparingTo("12.5");
        assertThat(plan.isActive()).isTrue();
    }

    private static void assertSamePlan(ManagementPlan actual, ManagementPlan expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    // ---------- Meteorologia diária ----------

    @Test
    void weatherRoundTripKeepsAllFields() {
        DailyWeather weather = new DailyWeather(UUID.randomUUID(), UUID.randomUUID(), DATE,
                new EtpDepth(bd("5.25")), new PrecipitationDepth(bd("12.5")));

        DailyWeatherEntity entity = weatherMapper.toEntity(weather);
        assertThat(entity.getWeatherDate()).isEqualTo(DATE);
        assertThat(entity.getEtpMm()).isEqualByComparingTo("5.25");
        assertThat(entity.getPrecipitationMm()).isEqualByComparingTo("12.5");

        assertSameWeather(weatherMapper.toDomain(entity), weather);
    }

    @Test
    void weatherRoundTripKeepsZeroValues() {
        DailyWeather weather = new DailyWeather(UUID.randomUUID(), UUID.randomUUID(), DATE,
                new EtpDepth(BigDecimal.ZERO), new PrecipitationDepth(BigDecimal.ZERO));

        DailyWeather back = weatherMapper.toDomain(weatherMapper.toEntity(weather));

        assertThat(back.getEtp().value()).isEqualByComparingTo("0");
        assertThat(back.getPrecipitation().value()).isEqualByComparingTo("0");
        assertSameWeather(back, weather);
    }

    @Test
    void weatherFromEntityWithScaleThreeAcceptsTrailingZeros() {
        DailyWeatherEntity entity = DailyWeatherEntity.newInstance();
        entity.setId(UUID.randomUUID());
        entity.setAreaId(UUID.randomUUID());
        entity.setWeatherDate(DATE);
        entity.setEtpMm(bd("12.500"));
        entity.setPrecipitationMm(bd("0.000"));

        DailyWeather weather = weatherMapper.toDomain(entity);

        assertThat(weather.getEtp().value()).isEqualByComparingTo("12.5");
        assertThat(weather.getPrecipitation().value()).isEqualByComparingTo("0");
    }

    private static void assertSameWeather(DailyWeather actual, DailyWeather expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    // ---------- Irrigação real ----------

    @Test
    void irrigationRoundTripKeepsAllFields() {
        AppliedIrrigation irrigation = new AppliedIrrigation(UUID.randomUUID(), UUID.randomUUID(), DATE,
                new AppliedIrrigationDepth(bd("8.75")));

        AppliedIrrigationEntity entity = irrigationMapper.toEntity(irrigation);
        assertThat(entity.getIrrigationDate()).isEqualTo(DATE);
        assertThat(entity.getAppliedIrrigationMm()).isEqualByComparingTo("8.75");

        assertSameIrrigation(irrigationMapper.toDomain(entity), irrigation);
    }

    @Test
    void irrigationRoundTripKeepsZeroAsPresentValue() {
        AppliedIrrigation irrigation = new AppliedIrrigation(UUID.randomUUID(), UUID.randomUUID(), DATE,
                new AppliedIrrigationDepth(BigDecimal.ZERO));

        AppliedIrrigationEntity entity = irrigationMapper.toEntity(irrigation);
        assertThat(entity.getAppliedIrrigationMm()).isNotNull().isEqualByComparingTo("0");

        AppliedIrrigation back = irrigationMapper.toDomain(entity);
        assertThat(back.getDepth().value()).isEqualByComparingTo("0");
        assertSameIrrigation(back, irrigation);
    }

    @Test
    void irrigationFromEntityWithScaleThreeAcceptsTrailingZeros() {
        AppliedIrrigationEntity entity = AppliedIrrigationEntity.newInstance();
        entity.setId(UUID.randomUUID());
        entity.setAreaId(UUID.randomUUID());
        entity.setIrrigationDate(DATE);
        entity.setAppliedIrrigationMm(bd("12.500"));

        assertThat(irrigationMapper.toDomain(entity).getDepth().value()).isEqualByComparingTo("12.5");
    }

    private static void assertSameIrrigation(AppliedIrrigation actual, AppliedIrrigation expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }
}
