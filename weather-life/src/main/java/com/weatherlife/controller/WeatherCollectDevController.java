package com.weatherlife.controller;

import com.weatherlife.service.WeatherCollectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/** Only for local testing. The route does not exist unless manual-enabled=true. */
@RestController
@RequestMapping("/api/internal/weather")
@ConditionalOnProperty(name = "weather.collection.manual-enabled", havingValue = "true")
public class WeatherCollectDevController {
    private final WeatherCollectionService collector;
    private final com.weatherlife.service.SeoulWeatherCollectionService seoulCollector;
    private final com.weatherlife.service.WeatherRetentionService retention;

    public WeatherCollectDevController(WeatherCollectionService collector,
                                       com.weatherlife.service.SeoulWeatherCollectionService seoulCollector,
                                       com.weatherlife.service.WeatherRetentionService retention) {
        this.collector = collector;
        this.seoulCollector = seoulCollector;
        this.retention = retention;
    }
    @PostMapping(value = "/collect", produces = MediaType.APPLICATION_JSON_VALUE)
    public WeatherCollectionService.CollectionResult collect(@RequestParam String regionName) {
        return collector.collect(regionName);
    }

    /** 개발 전용: 모든 서울 구를 한 번에 수집 (현재 CSV 기준 고유격자 14개). */
    @PostMapping(value = "/collect-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public com.weatherlife.service.SeoulWeatherCollectionService.BatchResult collectAll() {
        return seoulCollector.collectAll();
    }

    /** 개발 전용: 자정 이전 데이터 즉시 정리. */
    @PostMapping(value = "/cleanup", produces = MediaType.APPLICATION_JSON_VALUE)
    public java.util.Map<String,Integer> cleanup() {
        return java.util.Map.of("deleted", retention.deleteBeforeToday());
    }
}
