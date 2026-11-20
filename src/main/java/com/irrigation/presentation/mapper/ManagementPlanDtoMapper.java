package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.ManagementPlanInput;
import com.irrigation.application.dto.ManagementPlanOutput;
import com.irrigation.presentation.dto.ManagementPlanRequest;
import com.irrigation.presentation.dto.ManagementPlanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ManagementPlanDtoMapper {

    ManagementPlanInput toInput(ManagementPlanRequest request);

    ManagementPlanResponse toResponse(ManagementPlanOutput output);
}