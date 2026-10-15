package com.weatherlife.service;

import com.weatherlife.dto.OutfitRequest;
import java.util.ArrayList;
import java.util.List;
import static com.weatherlife.dto.OutfitTypes.*;

/** 날씨를 추천에 사용할 조건으로 변환합니다. 숫자 보정은 서비스용 경험 규칙입니다. */
public final class WeatherRules {
    private WeatherRules() { }
    public record Context(OutfitRequest request, double temperature, double outerTemperature,
                          boolean wet, boolean windy, boolean outerRequired,
                          OuterUsage outerUsage, boolean baseLayerRequired,
                          List<String> advice, List<String> warnings) { }

    public static Context evaluate(OutfitRequest r) {
        boolean home = r.occasion() == Occasion.HOME;
        boolean wet = !home && r.precipitation() != Precipitation.NONE;
        boolean windy = !home && r.windSpeed() >= 7;
        double t = adjust(r.temperature(), r);
        double outerT = home || r.minTemperature() == null ? t : adjust(r.minTemperature(), r);
        boolean outer = !home && (outerT < 20 || wet || windy);
        OuterUsage usage = !outer ? OuterUsage.NONE
                : (t >= 20 && !wet && !windy ? OuterUsage.CARRY : OuterUsage.WEAR);
        boolean base = t <= 5 && r.occasion() != Occasion.HOME
                && (r.occasion() != Occasion.EXERCISE || t <= 0);
        var advice = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        if (home) advice.add("HOME은 입력한 기온을 실내 온도로 해석하며 실외의 비·바람·일교차는 반영하지 않습니다.");
        if (!home && r.precipitation() == Precipitation.RAIN) advice.add("비에 대비해 우산과 방수 신발을 준비하세요.");
        if (!home && (r.precipitation() == Precipitation.SNOW || r.precipitation() == Precipitation.MIXED))
            advice.add("눈·진눈깨비에는 방수 기능과 접지력이 있는 신발을 선택하세요.");
        if (!home && windy) advice.add("바람이 강해 방풍 겉옷을 우선하고 바람에 날리기 쉬운 옷은 제외했습니다.");
        if (r.humidity() >= 70 && r.temperature() >= 23) advice.add("덥고 습한 조건에서는 통풍성과 빨리 마르는 소재에 가점을 줍니다.");
        if (!home && r.sky() == Sky.SUNNY) advice.add("맑은 날에는 모자와 햇빛 차단을 고려하세요. 하늘 상태만으로 자외선 지수를 계산하지는 않습니다.");
        if (!home && r.minTemperature() != null && r.maxTemperature() != null
                && r.maxTemperature() - r.minTemperature() >= 8)
            advice.add("일교차가 커서 겉옷은 최저기온을 기준으로 선택했습니다.");
        if (usage == OuterUsage.CARRY) advice.add("현재는 겉옷을 챙겨 두었다가 기온이 내려갈 때 걸치세요.");
        if (r.occasion() == Occasion.EXERCISE) advice.add("EXERCISE는 가벼운 야외 운동 기준입니다. 운동 강도·시간에 따라 조절하세요.");
        if (r.temperature() < -10 || r.temperature() > 35)
            warnings.add("극단적인 기온입니다. 기본 의류 조합만 제공하며 장시간 야외활동의 적합성을 보장하지 않습니다.");
        if (!home && r.windSpeed() >= 15)
            warnings.add("매우 강한 바람입니다. 의류 추천과 별도로 외출·야외 운동 여부를 판단하세요.");
        return new Context(r, round(t), round(outerT), wet, windy, outer, usage, base,
                List.copyOf(advice), List.copyOf(warnings));
    }

    // 공식 체감온도가 아닙니다. 예: 추위를 잘 타면 더 낮은 기온의 옷을 고릅니다.
    private static double adjust(double value, OutfitRequest r) {
        double t = value;
        if (r.occasion() != Occasion.HOME) {
            if (value >= 25 && r.humidity() >= 70) t += Math.min(2, (r.humidity() - 60) / 20);
            if (value >= 20 && r.sky() == Sky.SUNNY) t += 1;
            if (value <= 20 && r.windSpeed() >= 4) t -= Math.min(4, r.windSpeed() * 0.5);
            if (r.occasion() == Occasion.EXERCISE) t += 3;
        }
        if (r.thermalPreference() == ThermalPreference.FEEL_COLD) t -= 2;
        if (r.thermalPreference() == ThermalPreference.FEEL_HOT) t += 2;
        return Math.max(-50, Math.min(50, t));
    }

    private static double round(double n) { return Math.round(n * 10) / 10.0; }
}
