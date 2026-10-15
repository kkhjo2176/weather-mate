package com.weatherlife.service;

import com.weatherlife.dto.OutfitResponse;
import com.weatherlife.dto.TodayOutfitResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;

/** 저장된 DB 예보 조회 -> 시간별 OutfitRequest 변환 -> 의상 추천. */
@Service
public class TodayOutfitService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WeatherQueryService weatherQueryService;
    private final ShortForecastOutfitMapper mapper;
    private final OutfitService outfitService;

    public TodayOutfitService(WeatherQueryService weatherQueryService,
                             ShortForecastOutfitMapper mapper, OutfitService outfitService) {
        this.weatherQueryService = weatherQueryService;
        this.mapper = mapper;
        this.outfitService = outfitService;
    }

    public TodayOutfitResponse recommendToday(String regionName) {
        LocalDate today = LocalDate.now(KST);
        return recommendForDate(regionName, today);
    }

    /** 전체 날짜로 최저/최고기온을 계산한 후 해당 시각의 추천만 실행합니다. */
    public OutfitResponse recommendForHour(String regionName, LocalDateTime at) {
        var rows = weatherQueryService.loadDay(regionName, at.toLocalDate());
        var converted = mapper.convertStored(rows, at.toLocalDate());
        String hourKey = at.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        var request = converted.hourlyRequests().get(hourKey);
        if (request == null) throw new ForecastNotFoundException(at + " 의상 추천용 예보가 없습니다.");
        return outfitService.recommend(request);
    }

    public TodayOutfitResponse recommendForDate(String regionName, LocalDate date) {
        var rows = weatherQueryService.loadDay(regionName, date); // DB 조회만 수행
        var converted = mapper.convertStored(rows, date);

        // 정렬된 시간순 LinkedHashMap. 요청이 끝나면 응답으로 반환합니다.
        // 공유 캐시/DB에는 저장하지 않으므로 동시 요청에 의한 데이터 섞임이 없습니다.
        Map<String, OutfitResponse> recommendations = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>(converted.skippedHours());
        for (var entry : converted.hourlyRequests().entrySet()) {
            try {
                recommendations.put(entry.getKey(), outfitService.recommend(entry.getValue()));
            } catch (NoOutfitException ex) {
                // 한 시각의 의류 후보가 없더라도 다른 시간대의 결과는 반환합니다.
                skipped.add(entry.getKey());
            }
        }
        skipped.sort(String::compareTo);
        return new TodayOutfitResponse(date, regionName,
                Collections.unmodifiableMap(recommendations), List.copyOf(skipped));
    }
}
