package com.weatherlife.service;

import com.weatherlife.dto.OutfitResponse.AccessoryItem;
import com.weatherlife.dto.OutfitTypes.*;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** 필요한 소품만 반환합니다. 최소 개수를 채우는 로직은 없으며 빈 목록도 정상입니다. */
@Service
public class AccessoryService {
    private final AccessoryCatalog catalog;
    public AccessoryService(AccessoryCatalog catalog) { this.catalog = catalog; }

    public List<AccessoryItem> select(WeatherRules.Context ctx, OutfitSetCatalog.Definition set, OuterUsage outerUsage) {
        var r = ctx.request();
        double t = r.temperature();
        boolean rain = r.precipitation() == Precipitation.RAIN || r.precipitation() == Precipitation.MIXED;
        boolean sunny = r.sky() == Sky.SUNNY && !ctx.wet();
        var result = new ArrayList<AccessoryItem>();
        if (rain && r.windSpeed() < 10) add(result, "umbrella", "비 또는 진눈깨비 예보가 있습니다.");
        else if (!ctx.wet() && r.windSpeed() < 10 && r.precipitationProbability() != null && r.precipitationProbability() >= 40)
            add(result, "folding_umbrella", "강수확률이 40% 이상입니다.");
        if (rain && r.precipitationAmount() != null && r.precipitationAmount() >= 10)
            add(result, "rain_boots", "시간당 강수량이 10mm 이상이어서 추천 신발 대신 신을 장화를 고려하세요.");
        if (sunny && t >= 28 && !ctx.windy()) add(result, "parasol", "맑고 더운 날의 햇빛 대비용입니다.");
        if (sunny && t >= 20) add(result, "sunglasses", "맑은 날의 눈부심 대비용입니다.");
        if (!ctx.wet() && r.sky() != Sky.CLOUDY && t >= 18)
            add(result, "sunscreen", "맑거나 구름 많은 날의 외출 준비용입니다. 자외선 지수를 추정한 결과는 아닙니다.");
        if (sunny && t >= 25 && !ctx.windy()) add(result, "cap", "강한 햇빛에 대비합니다.");
        Double di = DiscomfortIndexCalculator.calculate(r.temperature().toString(), r.humidity().toString());
        if (t >= 30 || (di != null && di >= 80)) add(result, "fan", "높은 기온 또는 불쾌지수에 대비합니다.");
        if (t >= 28 || set.tags().contains("SPORT")) add(result, "water_bottle", "더운 날 또는 활동적인 의상 세트에 물병을 제안합니다.");
        if (t >= 30) add(result, "cool_sleeve", "30℃ 이상의 더운 날에 사용할 수 있습니다.");
        boolean wideRange = r.minTemperature() != null && r.maxTemperature() != null
                && r.maxTemperature() - r.minTemperature() >= 10;
        if (outerUsage == OuterUsage.CARRY || (outerUsage == OuterUsage.NONE && wideRange))
            add(result, "light_outer", "현재 세트와 별도로 일교차에 대비할 겉옷을 챙기세요.");
        if (t <= 5) add(result, "scarf", "5℃ 이하의 추위에 대비합니다.");
        if (t <= 0) add(result, "gloves", "0℃ 이하의 추위에 대비합니다.");
        if (t <= 0 || (t <= 5 && r.windSpeed() >= 5)) add(result, "beanie", "낮은 기온과 찬바람에 대비합니다.");
        if (t <= -5 || (t <= 3 && r.windSpeed() >= 8)) add(result, "earmuffs", "매우 춥거나 찬바람이 강합니다.");
        if (t <= -5) add(result, "hotpack", "-5℃ 이하의 추위에 대비합니다.");
        if (t <= 3) add(result, "thermos", "3℃ 이하의 추위에 따뜻한 음료를 챙길 수 있습니다.");
        if (r.humidity() <= 35) add(result, "lip_balm", "습도 35% 이하의 건조한 날입니다.");
        if (r.airQuality() == AirQuality.BAD || r.airQuality() == AirQuality.VERY_BAD)
            add(result, "mask", "전달된 미세먼지 등급이 나쁨 이상입니다.");
        return List.copyOf(result);
    }
    private void add(List<AccessoryItem> result, String key, String reason) {
        var item = catalog.item(key);
        result.add(new AccessoryItem(item.key(), item.name(), item.iconPath(), reason));
    }
}
