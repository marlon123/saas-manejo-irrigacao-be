package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.dto.SoilProfileOutput;
import com.irrigation.presentation.dto.SoilProfileRequest;
import com.irrigation.presentation.dto.SoilProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SoilProfileDtoMapper {

    SoilProfileInput toInput(SoilProfileRequest request);

    SoilProfileResponse toResponse(SoilProfileOutput output);
}
