package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.DailyWeatherInput;
import com.irrigation.application.dto.DailyWeatherOutput;
import com.irrigation.presentation.dto.DailyWeatherRequest;
import com.irrigation.presentation.dto.DailyWeatherResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DailyWeatherDtoMapper {

    DailyWeatherInput toInput(DailyWeatherRequest request);

    DailyWeatherResponse toResponse(DailyWeatherOutput output);
}