package com.weatherlife.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.util.concurrent.ThreadLocalRandom;
import static com.weatherlife.dto.OutfitTypes.*;

/** 단위: 기온 ℃, 습도 %, 풍속 m/s. 필수값은 temperature와 humidity입니다. */
public record OutfitRequest(
        @NotNull @DecimalMin("-50") @DecimalMax("50") Double temperature,//기준기온
        @NotNull @DecimalMin("0") @DecimalMax("100") Double humidity,//습도
        @DecimalMin("0") @DecimalMax("60") Double windSpeed,//풍속
        Sky sky, Precipitation precipitation, Gender gender,//성별
        Occasion occasion, Preference preference, ThermalPreference thermalPreference,
        @DecimalMin("-50") @DecimalMax("50") Double minTemperature,//최저기온
        @DecimalMin("-50") @DecimalMax("50") Double maxTemperature,//최고기온
        @Min(1) @Max(5) Integer count, Long seed) {//

    // 필드 생략 또는 null 입력 시 사용하는 기본값입니다.
    public OutfitRequest {
        windSpeed = windSpeed == null ? 0.0 : windSpeed;
        sky = sky == null ? Sky.CLOUDY : sky;
        precipitation = precipitation == null ? Precipitation.NONE : precipitation;
        gender = gender == null ? Gender.UNISEX : gender;
        occasion = occasion == null ? Occasion.DAILY : occasion;
        preference = preference == null ? Preference.ANY : preference;
        thermalPreference = thermalPreference == null ? ThermalPreference.NORMAL : thermalPreference;
        count = count == null ? 3 : count;
        seed = seed == null ? ThreadLocalRandom.current().nextLong() : seed;
    }

    // 이 API에서만 오타가 있는 필드를 거절합니다. 서버 전체 Jackson 설정은 바꾸지 않습니다.
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("지원하지 않는 옷 추천 필드: " + name);
    }

    @JsonIgnore
    @AssertTrue(message = "최저기온 ≤ 기준기온 ≤ 최고기온이어야 합니다.")
    public boolean isTemperatureRangeValid() {
        return temperature == null
                || ((minTemperature == null || minTemperature <= temperature)
                && (maxTemperature == null || maxTemperature >= temperature));
    }

    @JsonIgnore
    @AssertTrue(message = "기온·습도·풍속에는 유한한 숫자를 입력하세요.")
    public boolean isFiniteWeather() {
        return (temperature == null || Double.isFinite(temperature))
                && (humidity == null || Double.isFinite(humidity))
                && Double.isFinite(windSpeed)
                && (minTemperature == null || Double.isFinite(minTemperature))
                && (maxTemperature == null || Double.isFinite(maxTemperature));
    }
}
