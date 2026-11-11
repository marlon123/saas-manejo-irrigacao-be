package com.irrigation.presentation.mapper;

import com.irrigation.application.dto.CalculateRecommendationInput;
import com.irrigation.application.dto.CalculationParameters;
import com.irrigation.application.dto.CalculationResultOutput;
import com.irrigation.application.dto.RecommendationOutput;
import com.irrigation.presentation.dto.CalculateRecommendationRequest;
import com.irrigation.presentation.dto.CalculationParametersRequest;
import com.irrigation.presentation.dto.CalculationParametersResponse;
import com.irrigation.presentation.dto.CalculationResultResponse;
import com.irrigation.presentation.dto.RecommendationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecommendationDtoMapper {

    @Mapping(source = "inputs", target = "parameters")
    CalculateRecommendationInput toInput(CalculateRecommendationRequest request);

    CalculationParameters toParameters(CalculationParametersRequest request);

    RecommendationResponse toResponse(RecommendationOutput output);

    CalculationParametersResponse toResponse(CalculationParameters parameters);

    CalculationResultResponse toResponse(CalculationResultOutput result);
}
