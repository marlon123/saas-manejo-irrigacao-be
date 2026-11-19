package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.CropOutput;
import com.irrigation.application.dto.CropStageInput;
import com.irrigation.application.dto.CropStageOutput;
import com.irrigation.presentation.dto.CropRequest;
import com.irrigation.presentation.dto.CropResponse;
import com.irrigation.presentation.dto.CropStageRequest;
import com.irrigation.presentation.dto.CropStageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CropDtoMapper {

    /** A API usa {@code rootDepthMm} (com unidade); a camada de aplicação mantém {@code rootDepth}. */
    @Mapping(target = "rootDepth", source = "rootDepthMm")
    CropInput toInput(CropRequest request);

    CropStageInput toInput(CropStageRequest request);

    @Mapping(target = "rootDepthMm", source = "rootDepth")
    CropResponse toResponse(CropOutput output);

    CropStageResponse toResponse(CropStageOutput output);
}