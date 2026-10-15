package com.weatherlife.controller;

import com.weatherlife.service.ForecastNotFoundException;
import com.weatherlife.service.WeatherForecastMappingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {WeatherDatabaseController.class, WeatherCollectDevController.class})
public class WeatherDatabaseExceptionHandler {
    @ExceptionHandler(ForecastNotFoundException.class)
    ResponseEntity<Map<String,String>> notFound(ForecastNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("code","FORECAST_NOT_FOUND","message",e.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message",e.getMessage()));
    }
    @ExceptionHandler(WeatherForecastMappingException.class)
    ResponseEntity<Map<String,String>> badUpstream(WeatherForecastMappingException e) {
        return ResponseEntity.status(502).body(Map.of("code","INVALID_KMA_RESPONSE","message",e.getMessage()));
    }
}
