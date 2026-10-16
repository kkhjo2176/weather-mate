package com.weatherlife.controller;

import com.weatherlife.dto.AiForecastResponse;
import com.weatherlife.service.AiForecastService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 프론트 → Spring Boot → 별도 AI 서버 순서로 호출합니다. */
@RestController
@RequestMapping("/api/weather")
public class AiForecastController {
    private final AiForecastService service;

    public AiForecastController(AiForecastService service) {
        this.service = service;
    }

    /** GET /api/weather/ai?gu=영등포구 (regionName도 지원) */
    @GetMapping(value = "/ai", produces = MediaType.APPLICATION_JSON_VALUE)
    public AiForecastResponse forecast(@RequestParam(required = false) String gu,
                                       @RequestParam(required = false) String regionName) {
        String selected = gu != null && !gu.isBlank() ? gu : regionName;
        if (selected == null || selected.isBlank()) {
            throw new IllegalArgumentException("regionName 또는 gu 파라미터를 지정해 주세요.");
        }
        return service.getForecast(selected);
    }
}
