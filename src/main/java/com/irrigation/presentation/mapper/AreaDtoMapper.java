package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.AreaInput;
import com.irrigation.application.dto.AreaOutput;
import com.irrigation.presentation.dto.AreaRequest;
import com.irrigation.presentation.dto.AreaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AreaDtoMapper {

    AreaInput toInput(AreaRequest request);

    AreaResponse toResponse(AreaOutput output);
}
