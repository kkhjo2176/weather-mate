# 오늘의 시간별 의상 추천 (기상청 단기예보 연동)

## 추가한 API

`GET /api/outfits/today?regionName=서울특별시 영등포구 신길제1동`

- 서버에서 기존 `OfficialWeatherService.getShortRangeForecast(regionName)`을 **한 번만 호출**합니다.
- 기상청 JSON의 `response.body.items.item` 목록을 `fcstDate + fcstTime`으로 묶습니다.
- 서버의 한국 시간(`Asia/Seoul`) 기준 **오늘(`yyyyMMdd`)에 해당하는 예보만** 변환합니다.
- `OutfitService.recommend()`로 각 시간마다 코디 3벌(기본값)을 만든 후, 시간순 `LinkedHashMap`에 저장하여 반환합니다.
- 전역 캐시나 DB 저장은 하지 않습니다. API 요청의 결과 데이터에만 Map이 존재합니다.
- 프론트 담당자와 시간/선호도 정책을 정하면 이 API 또는 `TodayOutfitService`를 확장하면 됩니다.

## 매핑 기준

| 기상청 category | OutfitRequest | 처리 |
| --- | --- | --- |
| TMP | temperature | 숫자 파싱 (℃) |
| REH | humidity | 숫자 파싱 (%) |
| WSD | windSpeed | 숫자 파싱 (m/s) |
| SKY | sky | 1=SUNNY, 3=PARTLY_CLOUDY, 4=CLOUDY |
| PTY | precipitation | 0=NONE, 1/4/5=RAIN, 2/6=MIXED, 3/7=SNOW |
| TMN, TMX + 오늘의 TMP | minTemperature / maxTemperature | 오늘 전체 예보를 대상으로 하루 최저·최고 산출. 시간마다 동일값 사용 |

`POP`, `PCP`, `SNO`, `UUU`, `VVV`, `VEC`, `WAV` 등은 현행 의상 추천 DTO의 직접 입력이 아니라서 이번 변환에 사용하지 않습니다.

`gender`=UNISEX, `occasion`=DAILY, `preference`=ANY, `thermalPreference`=NORMAL, `count`=3은 `OutfitRequest`의 기존 기본값을 사용합니다. `seed`는 요청을 만들 때마다 랜덤으로 생성됩니다. 기존 `POST /api/outfits/recommend` 역시 seed를 생략하면 랜덤, 명시하면 해당 시드로 재현 가능합니다.

예보값 중 `TMP`, `REH`, `WSD`, `SKY`, `PTY`가 없거나 잘못된 시각은 임의로 꾸며내지 않고 `skippedHours`에 기록하여 제외합니다. 의류 조건에 맞는 후보가 없는 시각도 제외합니다. TMN/TMX는 일부 시각에만 제공되므로 시간별 필수 조건이 아닙니다.

## 응답 형태 (예시: 의상 항목은 일부 생략)

```json
{
  "date": "2026-10-14",
  "regionName": "서울특별시 영등포구 신길제1동",
  "hourlyRecommendations": {
    "09:00": {
      "recommendationType": "RULE_BASED",
      "input": {
        "temperature": 14.0,
        "humidity": 40.0,
        "windSpeed": 2.9,
        "sky": "SUNNY",
        "precipitation": "NONE",
        "gender": "UNISEX",
        "occasion": "DAILY",
        "preference": "ANY",
        "thermalPreference": "NORMAL",
        "minTemperature": 14.0,
        "maxTemperature": 19.0,
        "count": 3,
        "seed": 1435212317312
      },
      "outfits": ["실제 응답에서는 코디 객체 3개가 이 배열에 포함됩니다"]
    },
    "10:00": { "...": "다음 시간대 추천 결과" }
  },
  "skippedHours": []
}
```

위 JSON은 형태 안내 예시로, `outfits`는 실제 의상 객체 대신 설명용 문자열로 간략화했습니다. `seed`도 예시입니다.

## API 확인

```http
GET http://localhost:8080/api/outfits/today?regionName=서울특별시 영등포구 신길제1동
```

`GET /api/short`, `POST /api/outfits/recommend`, `GET /api/outfits/catalog`은 그대로 사용합니다.

**주의할 점**

- 최신 단기예보 발표 시각부터의 예보를 가져오기 때문에, 이미 지난 '오늘'의 시간은 빠질 수 있습니다.
- 늦은 밤에는 최신 발표 내용에 오늘 날짜가 없어 `hourlyRecommendations: {}`가 나올 수 있습니다. 오류가 아니라 오늘 예보가 없다는 뜻입니다.
- 지금의 일 최저·최고기온은 `외출 시간대`가 아니라 **오늘 제공된 모든 시간대**를 기준으로 계산합니다. 실제 외출시간 선택 기능은 프론트 협의 후 추가하면 됩니다.
- KMA 응답이 잘못되었거나 오류 코드인 경우 HTTP 502, 지역명이 잘못된 경우 HTTP 400을 반환합니다.
- 매번 실제 기상청을 호출합니다. 캐시/호출 주기 제한은 아직 적용하지 않았습니다.
- Gradle의 `spring-boot-starter-json`은 기존 의류 카탈로그의 Jackson 3 `JsonMapper`와 시간별 결과 JSON 직렬화에 필요합니다.

## 주요 파일

- `service/ShortForecastOutfitMapper.java` — 기상청 응답 → 시간별 OutfitRequest Map
- `service/TodayOutfitService.java` — 단기예보 1회 호출 → 시간별 코디 추천 Map
- `controller/TodayOutfitController.java` — 신규 API 엔드포인트
- `controller/TodayOutfitExceptionHandler.java` — 신규 API에만 적용되는 오류 처리
- `dto/TodayOutfitResponse.java` — 최종 응답 타입
- `dto/OutfitRequest.java` — seed 생략 시 난수 기본값
