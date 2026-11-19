package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.dto.SoilProfileOutput;
import com.irrigation.presentation.dto.SoilProfileRequest;
import com.irrigation.presentation.dto.SoilProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SoilProfileDtoMapper {

    /** A API usa o sufixo {@code Pct} nos campos gravimétricos; a camada de aplicação não o usa. */
    @Mapping(target = "fieldCapacityGravimetric", source = "fieldCapacityGravimetricPct")
    @Mapping(target = "permanentWiltingPointGravimetric", source = "permanentWiltingPointGravimetricPct")
    SoilProfileInput toInput(SoilProfileRequest request);

    @Mapping(target = "fieldCapacityGravimetricPct", source = "fieldCapacityGravimetric")
    @Mapping(target = "permanentWiltingPointGravimetricPct", source = "permanentWiltingPointGravimetric")
    SoilProfileResponse toResponse(SoilProfileOutput output);
}