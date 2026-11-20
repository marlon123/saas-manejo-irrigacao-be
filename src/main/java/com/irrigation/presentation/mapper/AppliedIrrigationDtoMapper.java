package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.AppliedIrrigationInput;
import com.irrigation.application.dto.AppliedIrrigationOutput;
import com.irrigation.presentation.dto.AppliedIrrigationRequest;
import com.irrigation.presentation.dto.AppliedIrrigationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AppliedIrrigationDtoMapper {

    AppliedIrrigationInput toInput(AppliedIrrigationRequest request);

    AppliedIrrigationResponse toResponse(AppliedIrrigationOutput output);
}