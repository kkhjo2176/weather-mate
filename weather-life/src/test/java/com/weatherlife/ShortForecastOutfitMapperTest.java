package com.weatherlife;

import com.weatherlife.dto.OutfitTypes.Precipitation;
import com.weatherlife.dto.OutfitTypes.Sky;
import com.weatherlife.service.ShortForecastOutfitMapper;
import com.weatherlife.service.WeatherForecastMappingException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** API 호출 없이 기상청 원본 category 행 -> 시간별 DTO 변환을 검사합니다. */
class ShortForecastOutfitMapperTest {
    private final ShortForecastOutfitMapper mapper = new ShortForecastOutfitMapper();
    private final LocalDate today = LocalDate.of(2026, 10, 14);

    static String row(String date, String time, String category, String value) {
        return "{\"fcstDate\":\"" + date + "\",\"fcstTime\":\"" + time
                + "\",\"category\":\"" + category + "\",\"fcstValue\":\"" + value + "\"}";
    }

    static String forecast(String... rows) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL_SERVICE\"},"
                + "\"body\":{\"dataType\":\"JSON\",\"items\":{\"item\":["
                + String.join(",", rows) + "]}}}}";
    }

    @Test void groupsTodayHourlyAndConvertsWeatherCodes() {
        String json = forecast(
                row("20261015", "0900", "TMP", "30"), // 다른 날짜 제외
                row("20261014", "1000", "TMP", "15"),
                row("20261014", "1000", "REH", "35"),
                row("20261014", "1000", "WSD", "3"),
                row("20261014", "1000", "SKY", "3"),
                row("20261014", "1000", "PTY", "1"),
                row("20261014", "0900", "TMP", "14"),
                row("20261014", "0900", "WSD", "2.9"),
                row("20261014", "0900", "SKY", "1"),
                row("20261014", "0900", "PTY", "0"),
                row("20261014", "0900", "REH", "40"),
                row("20261014", "1500", "TMX", "19"),
                row("20261014", "0600", "TMN", "10")
        );
        var converted = mapper.convert(json, today);
        assertThat(converted.hourlyRequests().keySet()).containsExactly("09:00", "10:00");
        assertThat(converted.skippedHours()).containsExactly("06:00", "15:00");
        var atNine = converted.hourlyRequests().get("09:00");
        assertThat(atNine.temperature()).isEqualTo(14);
        assertThat(atNine.humidity()).isEqualTo(40);
        assertThat(atNine.windSpeed()).isEqualTo(2.9);
        assertThat(atNine.sky()).isEqualTo(Sky.SUNNY);
        assertThat(atNine.precipitation()).isEqualTo(Precipitation.NONE);
        assertThat(atNine.minTemperature()).isEqualTo(10);
        assertThat(atNine.maxTemperature()).isEqualTo(19);
        assertThat(atNine.gender().name()).isEqualTo("UNISEX");
        assertThat(atNine.occasion().name()).isEqualTo("DAILY");
        assertThat(atNine.count()).isEqualTo(3);
        var atTen = converted.hourlyRequests().get("10:00");
        assertThat(atTen.sky()).isEqualTo(Sky.PARTLY_CLOUDY);
        assertThat(atTen.precipitation()).isEqualTo(Precipitation.RAIN);
    }

    @Test void missingRequiredWeatherDoesNotBecomeInventedSunnyOrCalm() {
        var converted = mapper.convert(forecast(
                row("20261014", "0900", "TMP", "15"),
                row("20261014", "0900", "REH", "40"),
                row("20261014", "0900", "PTY", "0"), // SKY, WSD 없음
                row("20261014", "1000", "TMP", "16"),
                row("20261014", "1000", "REH", "42"),
                row("20261014", "1000", "WSD", "2"),
                row("20261014", "1000", "SKY", "4"),
                row("20261014", "1000", "PTY", "7")
        ), today);
        assertThat(converted.skippedHours()).containsExactly("09:00");
        assertThat(converted.hourlyRequests()).containsOnlyKeys("10:00");
        assertThat(converted.hourlyRequests().get("10:00").precipitation()).isEqualTo(Precipitation.SNOW);
        assertThat(converted.hourlyRequests().get("10:00").sky()).isEqualTo(Sky.CLOUDY);
    }

    @Test void seedIsRandomForNewRequestsUnlessExplicitlyGiven() {
        var json = forecast(
                row("20261014", "0900", "TMP", "14"),
                row("20261014", "0900", "REH", "40"),
                row("20261014", "0900", "WSD", "2.9"),
                row("20261014", "0900", "SKY", "1"),
                row("20261014", "0900", "PTY", "0")
        );
        var seeds = new java.util.HashSet<Long>();
        for (int i = 0; i < 10; i++) {
            seeds.add(mapper.convert(json, today).hourlyRequests().get("09:00").seed());
        }
        assertThat(seeds.size()).isGreaterThan(1);
    }

    @Test void errorResponseAndMissingStructureAreNotSilentlyAccepted() {
        assertThatThrownBy(() -> mapper.convert("{\"response\":{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"ERROR\"}}", today))
                .isInstanceOf(WeatherForecastMappingException.class).hasMessageContaining("03");
        assertThatThrownBy(() -> mapper.convert("{}", today))
                .isInstanceOf(WeatherForecastMappingException.class);
    }

    @Test void futureOnlyForecastProducesEmptyMap() {
        var result = mapper.convert(forecast(row("20261015", "0900", "TMP", "22")), today);
        assertThat(result.hourlyRequests()).isEmpty();
        assertThat(result.skippedHours()).isEqualTo(List.of());
    }
}
