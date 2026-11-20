package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.application.dto.ReprocessWaterBalanceInput;
import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.dto.WaterBalanceOutput.OperatingTimeOutput;
import com.irrigation.application.dto.WaterBalanceOutput.WaterBalanceControlOutput;
import com.irrigation.application.dto.WaterBalanceOutput.WaterBalanceInputsOutput;
import com.irrigation.application.dto.WaterBalanceOutput.WaterBalanceResultsOutput;
import com.irrigation.presentation.dto.CalculateWaterBalanceRequest;
import com.irrigation.presentation.dto.DailyWaterBalanceResponse;
import com.irrigation.presentation.dto.DailyWaterBalanceResponse.ControlResponse;
import com.irrigation.presentation.dto.DailyWaterBalanceResponse.InputsResponse;
import com.irrigation.presentation.dto.DailyWaterBalanceResponse.OperatingTimeResponse;
import com.irrigation.presentation.dto.DailyWaterBalanceResponse.ResultsResponse;
import com.irrigation.presentation.dto.ReprocessWaterBalanceRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WaterBalanceDtoMapper {

    CalculateWaterBalanceInput toInput(CalculateWaterBalanceRequest request);

    ReprocessWaterBalanceInput toInput(ReprocessWaterBalanceRequest request);

    DailyWaterBalanceResponse toResponse(WaterBalanceOutput output);

    @Mapping(target = "wiltingPointPct", source = "wiltingPointPct")
    InputsResponse toResponse(WaterBalanceInputsOutput inputs);

    ResultsResponse toResponse(WaterBalanceResultsOutput results);

    OperatingTimeResponse toResponse(OperatingTimeOutput operatingTime);

    ControlResponse toResponse(WaterBalanceControlOutput control);
}