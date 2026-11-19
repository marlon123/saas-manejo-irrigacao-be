package com.irrigation.support;

import com.irrigation.application.dto.AreaInput;
import com.irrigation.application.dto.CalculationParameters;
import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.AppliedIrrigationInput;
import com.irrigation.application.dto.CropStageInput;
import com.irrigation.application.dto.DailyWeatherInput;
import com.irrigation.application.dto.ManagementPlanInput;
import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.CropStage;
import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Fábricas de dados válidos para os testes.
 */
public final class TestData {

    private TestData() {
    }

    public static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ---------------------------------------------------------------- Área

    public static Area area(UUID id, String name, String hectares) {
        return new Area(id, name, new AreaInHectares(bd(hectares)), null, "solo argiloso",
                IrrigationSystemType.DRIP, new FlowRate(bd("120.50")), new IrrigationEfficiency(bd("0.85")));
    }

    public static Area area() {
        return area(UUID.randomUUID(), "Talhão 1", "10.50");
    }

    public static AreaInput areaInput() {
        return new AreaInput("Talhão 1", bd("10.50"), bd("-23.5"), bd("-46.6"), "solo argiloso",
                IrrigationSystemType.DRIP, bd("120.50"), bd("0.85"));
    }

    // ------------------------------------------------------------- Cultura

    public static Crop crop() {
        return new Crop(UUID.randomUUID(), "Milho", "Híbrido", 120,
                List.of(new CropStage("Inicial", new Kc(bd("0.40")), 1),
                        new CropStage("Pleno", new Kc(bd("1.15")), 2)));
    }

    public static CropInput cropInput() {
        return new CropInput("Milho", "Híbrido", 120, List.of(
                new CropStageInput("Inicial", bd("0.40"), 1),
                new CropStageInput("Pleno", bd("1.15"), 2)), bd("1.00"), bd("400"));
        // Revisão A-01 (Req 2.10): kc e rootDepth passaram a ser obrigatórios na API de cultura.
    }

    // ---------------------------------------------------------------- Solo

    public static SoilProfile soilProfile() {
        return new SoilProfile(UUID.randomUUID(), "Argiloso",
                new VolumetricWaterContent(bd("0.35")), new VolumetricWaterContent(bd("0.18")),
                new BulkDensity(bd("1.30")), new RootDepth(bd("600.00")), new AvailableWater(bd("100.00")),
                MeasurementUnit.VOLUMETRIC_WATER_CONTENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS);
    }

    public static SoilProfileInput soilProfileInput() {
        return new SoilProfileInput("Argiloso", bd("0.35"), bd("0.18"), bd("1.30"), bd("600.00"), bd("100.00"),
                MeasurementUnit.VOLUMETRIC_WATER_CONTENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS);
    }

    // -------------------------------------------------------------- Cálculo

    public static CalculationInput calculationInput() {
        return new CalculationInput(bd("5"), new Kc(bd("1.0")), bd("5"), bd("0"), bd("50"), bd("100"),
                new IrrigationEfficiency(bd("0.8")), bd("20"), bd("25"), bd("10"), bd("100"), bd("2"));
    }

    public static CalculationParameters calculationParameters() {
        return CalculationParameters.from(calculationInput());
    }

    public static CalculationResult calculationResult() {
        return new CalculationResult(bd("20"), bd("2000"), bd("2"));
    }

    public static Recommendation recommendation(UUID areaId, LocalDateTime dateTime) {
        return new Recommendation(UUID.randomUUID(), areaId, dateTime, calculationInput(), calculationResult(),
                new MethodologyIdentifier("fake"), "1.0");
    }

    // ------------------------------------------------- Balanço hídrico (Fase 3)

    /** Data atual fixa dos testes do módulo de balanço hídrico. */
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    /** Relógio fixo em UTC, em {@link #TODAY} ao meio-dia. */
    public static Clock fixedClock() {
        return Clock.fixed(TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    /** Plano ativo (sem data final) com F = 0,5 e taxa = 10 mm/h. */
    public static ManagementPlan managementPlan(UUID areaId, UUID cropId, UUID soilProfileId, LocalDate startDate) {
        return new ManagementPlan(UUID.randomUUID(), areaId, cropId, soilProfileId, startDate,
                new AvailabilityFactor(bd("0.5")), new SprinklerRate(bd("10")));
    }

    /** Plano encerrado em {@code endDate}. */
    public static ManagementPlan managementPlan(UUID areaId, UUID cropId, UUID soilProfileId,
                                                LocalDate startDate, LocalDate endDate) {
        return new ManagementPlan(UUID.randomUUID(), areaId, cropId, soilProfileId, startDate, endDate,
                new AvailabilityFactor(bd("0.5")), new SprinklerRate(bd("10")));
    }

    public static ManagementPlanInput managementPlanInput(UUID cropId, UUID soilProfileId, LocalDate startDate) {
        return new ManagementPlanInput(cropId, soilProfileId, startDate, null, bd("0.5"), bd("10"));
    }

    public static DailyWeather dailyWeather(UUID areaId, LocalDate date, String etpMm, String precipitationMm) {
        return new DailyWeather(UUID.randomUUID(), areaId, date, new EtpDepth(bd(etpMm)),
                new PrecipitationDepth(bd(precipitationMm)));
    }

    public static DailyWeatherInput dailyWeatherInput(LocalDate date, String etpMm, String precipitationMm) {
        return new DailyWeatherInput(date, bd(etpMm), bd(precipitationMm));
    }

    public static AppliedIrrigation appliedIrrigation(UUID areaId, LocalDate date, String depthMm) {
        return new AppliedIrrigation(UUID.randomUUID(), areaId, date, new AppliedIrrigationDepth(bd(depthMm)));
    }

    public static AppliedIrrigationInput appliedIrrigationInput(LocalDate date, String depthMm) {
        return new AppliedIrrigationInput(date, bd(depthMm));
    }
}
