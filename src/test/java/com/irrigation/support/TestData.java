package com.irrigation.support;

import com.irrigation.application.dto.AreaInput;
import com.irrigation.application.dto.CalculationParameters;
import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.CropStageInput;
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
import java.time.LocalDateTime;
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
                new CropStageInput("Pleno", bd("1.15"), 2)));
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
}
