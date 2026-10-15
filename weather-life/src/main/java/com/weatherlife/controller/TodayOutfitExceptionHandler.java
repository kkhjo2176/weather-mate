package com.weatherlife.controller;

import com.weatherlife.service.NoOutfitException;
import com.weatherlife.service.ForecastNotFoundException;
import com.weatherlife.service.WeatherForecastMappingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** 신규 시간별 추천 API의 오류만 처리하고 기존 API의 예외 동작에는 관여하지 않습니다. */
@RestControllerAdvice(assignableTypes = TodayOutfitController.class)
public class TodayOutfitExceptionHandler {
    public record ApiError(String code, String message, List<String> details) { }

    @ExceptionHandler(ForecastNotFoundException.class)
    ResponseEntity<ApiError> notFound(ForecastNotFoundException ex) {
        return ResponseEntity.status(404).body(new ApiError("FORECAST_NOT_FOUND", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(WeatherForecastMappingException.class)
    ResponseEntity<ApiError> invalidForecast(WeatherForecastMappingException ex) {
        return ResponseEntity.status(502).body(
                new ApiError("INVALID_KMA_RESPONSE", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(NoOutfitException.class)
    ResponseEntity<ApiError> noOutfit(NoOutfitException ex) {
        return ResponseEntity.status(422).body(
                new ApiError("NO_MATCHING_OUTFIT", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> unknownRegion(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                new ApiError("INVALID_REGION", ex.getMessage(), List.of()));
    }
}
