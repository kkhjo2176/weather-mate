package com.weatherlife.service;

import com.weatherlife.dto.OutfitRequest;
import com.weatherlife.entity.HourlyForecast;
import com.weatherlife.dto.OutfitTypes.Precipitation;
import com.weatherlife.dto.OutfitTypes.Sky;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /api/short의 기상청 원본 응답을 OutfitRequest로 변환합니다.
 * 기상청 데이터는 한 시간의 모든 값이 한 객체에 들어 있는 게 아니라 category별 행으로 나뉩니다.
 * 따라서 (fcstDate, fcstTime)별로 묶고, 서울 기준 오늘 날짜만 추려서 변환합니다.
 */
@Component
public class ShortForecastOutfitMapper {
    private static final DateTimeFormatter KMA_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter KMA_TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter OUTPUT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    public record ConversionResult(Map<String, OutfitRequest> hourlyRequests, List<String> skippedHours) { }

    public ConversionResult convert(String rawJson, LocalDate today) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new WeatherForecastMappingException("기상청에서 빈 단기예보 응답을 받았습니다.");
        }

        Map<String, Object> root;
        try {
            root = JsonParserFactory.getJsonParser().parseMap(rawJson);
        } catch (RuntimeException ex) {
            throw new WeatherForecastMappingException("기상청 단기예보 JSON을 파싱하지 못했습니다.", ex);
        }
        if (root == null) {
            throw new WeatherForecastMappingException("기상청 단기예보 JSON이 객체 형식이 아닙니다.");
        }

        var response = requiredMap(root.get("response"), "response");
        var header = requiredMap(response.get("header"), "response.header");
        String resultCode = str(header.get("resultCode"));
        if (!"00".equals(resultCode)) {
            throw new WeatherForecastMappingException("기상청 단기예보 조회 오류 (" + resultCode
                    + "): " + str(header.get("resultMsg")));
        }
        var body = requiredMap(response.get("body"), "response.body");
        Object rawItems = body.get("items");
        if (rawItems == null || "".equals(rawItems)) {
            return new ConversionResult(Map.of(), List.of());
        }
        var items = requiredMap(rawItems, "response.body.items");
        Object itemValue = items.get("item");
        if (itemValue == null || "".equals(itemValue)) {
            return new ConversionResult(Map.of(), List.of());
        }
        if (!(itemValue instanceof List<?> rows)) {
            throw new WeatherForecastMappingException("response.body.items.item이 배열이 아닙니다.");
        }

        // 시간별로 category 값을 모은 뒤, 현재 날짜(Asia/Seoul)만 남깁니다.
        TreeMap<LocalTime, Map<String, String>> hourly = new TreeMap<>();
        for (Object value : rows) {
            if (!(value instanceof Map<?, ?> row)) continue;
            LocalDate date;
            LocalTime time;
            try {
                date = LocalDate.parse(str(row.get("fcstDate")), KMA_DATE);
                time = LocalTime.parse(str(row.get("fcstTime")), KMA_TIME);
            } catch (DateTimeParseException ex) {
                continue; // 날짜/시간이 없는 행은 매핑 대상에서 제외
            }
            if (!today.equals(date)) continue;
            String category = str(row.get("category"));
            String forecastValue = str(row.get("fcstValue"));
            hourly.computeIfAbsent(time, ignored -> new LinkedHashMap<>())
                    .put(category, forecastValue);
        }

        return convertGrouped(hourly);
    }

    /** DB에서 로드한 정규화된 시간별 행을 그대로 추천용 입력으로 변환합니다. */
    public ConversionResult convertStored(java.util.List<HourlyForecast> stored, LocalDate date) {
        TreeMap<LocalTime, Map<String,String>> hourly = new TreeMap<>();
        for (HourlyForecast row : stored) {
            if (row.getForecastAt().toLocalDate().equals(date)) {
                hourly.put(row.getForecastAt().toLocalTime(), row.categories());
            }
        }
        return convertGrouped(hourly);
    }

    private ConversionResult convertGrouped(TreeMap<LocalTime, Map<String,String>> hourly) {
        // TMN/TMX는 매시간 나오지 않습니다. 시간별 TMP 전체 및 TMN/TMX로
        // '오늘의 최저·최고'를 한 번 계산한 다음 모든 시간의 추천에 공통 적용합니다.
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Map<String, String> values : hourly.values()) {
            Double current = parseNumber(values.get("TMP"));
            Double dailyMin = parseNumber(values.get("TMN"));
            Double dailyMax = parseNumber(values.get("TMX"));
            if (current != null) { min = Math.min(min, current); max = Math.max(max, current); }
            if (dailyMin != null) min = Math.min(min, dailyMin);
            if (dailyMax != null) max = Math.max(max, dailyMax);
        }
        Double minTemperature = Double.isFinite(min) ? min : null;
        Double maxTemperature = Double.isFinite(max) ? max : null;

        Map<String, OutfitRequest> result = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        for (var hour : hourly.entrySet()) {
            String time = hour.getKey().format(OUTPUT_TIME);
            Map<String, String> weather = hour.getValue();
            Double temperature = parseNumber(weather.get("TMP"));
            Double humidity = parseNumber(weather.get("REH"));
            Double windSpeed = parseNumber(weather.get("WSD"));
            Sky sky = toSky(weather.get("SKY"));
            Precipitation rain = toPrecipitation(weather.get("PTY"));

            // 필수 날씨값이 없으면 기본값으로 날씨를 꾸미지 않고 해당 시간만 제외합니다.
            if (temperature == null || humidity == null || windSpeed == null || sky == null || rain == null
                    || temperature < -50 || temperature > 50
                    || humidity < 0 || humidity > 100 || windSpeed < 0 || windSpeed > 60) {
                skipped.add(time);
                continue;
            }
            // 성별·용도·선호도는 null -> OutfitRequest의 기본값 적용.
            // seed도 시간마다 새 난수로 생성하므로 매번 다양한 추천을 만들 수 있습니다.
            OutfitRequest request = new OutfitRequest(
                    temperature, humidity, windSpeed, sky, rain,
                    null, null, null, null,
                    minTemperature, maxTemperature, null,
                    ThreadLocalRandom.current().nextLong()
            );
            result.put(time, request);
        }
        return new ConversionResult(Collections.unmodifiableMap(result), List.copyOf(skipped));
    }

    private static Sky toSky(String value) {
        return switch (value == null ? "" : value) {
            case "1" -> Sky.SUNNY;
            case "3" -> Sky.PARTLY_CLOUDY;
            case "4" -> Sky.CLOUDY;
            default -> null;
        };
    }

    private static Precipitation toPrecipitation(String value) {
        // 단기예보의 5~7: 빗방울 / 빗방울눈날림 / 눈날림
        return switch (value == null ? "" : value) {
            case "0" -> Precipitation.NONE;
            case "1", "4", "5" -> Precipitation.RAIN;
            case "2", "6" -> Precipitation.MIXED;
            case "3", "7" -> Precipitation.SNOW;
            default -> null;
        };
    }

    private static Double parseNumber(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requiredMap(Object object, String name) {
        if (!(object instanceof Map<?, ?> map)) {
            throw new WeatherForecastMappingException(name + " 필드가 없거나 객체가 아닙니다.");
        }
        return (Map<String, Object>) map;
    }
}
