# Weather Life

기상청 단기예보를 수집해 DB에 저장하고, 저장된 예보로 시간별 날씨와 복장·소품 추천을 제공합니다.

## 먼저 볼 파일

| 작업 | 파일 (`src/main/java/com/weatherlife/` 아래) |
|---|---|
| 날짜·시각별 날씨 API | `controller/WeatherDatabaseController.java` |
| 날짜·시각별 복장·소품 API | `controller/TodayOutfitController.java` |
| 수동 기상청 호출·DB 저장 API | `controller/WeatherCollectDevController.java` |
| 기상청 호출 | `service/OfficialWeatherService.java` |
| 응답 검증·DB 저장/갱신 | `service/WeatherCollectionService.java` |
| 서울 전체 수집 | `service/SeoulWeatherCollectionService.java` |
| 저장된 날씨 조회 | `service/WeatherQueryService.java` |
| DB 예보를 추천 입력으로 변환 | `service/ShortForecastOutfitMapper.java` |
| 날짜·시각별 추천 실행 | `service/TodayOutfitService.java` |
| 복장 선택 / 소품 선택 | `service/OutfitService.java` / `service/AccessoryService.java` |
| 자동 수집·오래된 예보 정리 | `scheduler/WeatherCollectScheduler.java` |

수동 수집은 기상청 호출 후 `WeatherCollectionService`에서 DB에 저장합니다.
아래 조회 API 4개는 DB만 읽으며 기상청 API를 다시 호출하지 않습니다.

## 핵심 API

| 메서드 | 경로 | 주요 파라미터 | 응답 |
|---|---|---|---|
| GET | `/api/weather/short` | `gu`, `date` | 지정 날짜의 시간별 날씨 |
| GET | `/api/weather/hour` | `gu`, `at` | 지정 시각의 날씨 |
| GET | `/api/outfits/by-date` | `gu`, `date`, `gender` | 지정 날짜의 시간별 복장·소품 |
| GET | `/api/outfits/by-hour` | `gu`, `at`, `gender` | 지정 시각의 복장·소품 |

- 지역은 `gu=영등포구` 또는 `regionName=서울특별시 영등포구`로 전달합니다. 둘 다 있으면 `gu`가 우선입니다.
- `regionName`에는 CSV에 있는 동 단위 정식 명칭도 사용할 수 있습니다.
- 날짜는 `YYYY-MM-DD`, 시각은 `YYYY-MM-DDTHH:mm:ss`이며 한국 시각 기준입니다.
- `/api/weather/short`의 날짜를 생략하면 한국 기준 오늘입니다. 복장 날짜 조회에는 `date`가 필요합니다.
- 복장은 `gender=FEMALE|MALE|UNISEX`, `count=1~5`, `seed`를 선택적으로 받습니다. 기본 추천 수는 1입니다.
- 수집하지 않은 지역·시각은 404입니다. 먼저 수동 수집을 실행하세요.

호출 예시는 루트의 [`weather-requests.http`](weather-requests.http)에 있습니다.
복장 응답의 세부 필드와 규칙은 [`README-OUTFITS.md`](README-OUTFITS.md)를 참고하세요.

## 수동 호출·DB 저장

`src/main/resources/application.properties`의 아래 설정이 `true`일 때 수동 API가 등록됩니다.
첨부 원본의 `true` 설정을 유지했습니다.

```properties
weather.collection.manual-enabled=true
```

| 메서드 | 경로 | 처리 |
|---|---|---|
| POST | `/api/internal/weather/collect?regionName=영등포구` | 지정 지역의 최신 단기예보 호출·DB 저장 |
| POST | `/api/internal/weather/collect-all` | 서울 25개 구를 대표하는 고유 격자별 수집·저장 |
| POST | `/api/internal/weather/cleanup` | 한국 기준 오늘 자정 이전 예보 삭제 |

같은 격자·예보 시각의 데이터는 중복 생성하지 않으며, 발표 시각이 더 최신일 때 갱신합니다.
`collect-all`은 수집 전에 오늘 이전의 예보도 정리합니다. 이 동작은 원본과 같습니다.

자동 수집은 별도 설정 `weather.collection.enabled=true`로 켭니다. 원본에서 해당 설정이 생략되어 있어 기본값 `false`인 상태를 유지했습니다.
켜면 한국 기준 02/05/08/11/14/17/20/23시 20분에 수집하고, 매일 00:05 및 시작 시 오래된 예보를 정리합니다.

## 함께 유지한 기능

아래는 이미 동작하는 별도 API이므로 유지했습니다.

| 경로 | 용도 |
|---|---|
| `/api/weather/districts` | 서울 자치구 목록 |
| `/api/outfits/today` | 오늘의 시간별 추천 |
| `/api/outfits/recommend` | 직접 전달한 날씨로 추천 (POST) |
| `/api/outfits/catalog`, `/api/outfits/sets`, `/api/outfits/accessories` | 의류·세트·소품 목록 |
| `/api/short` | 기상청 원본 단기예보 직접 조회 |
| `/api/medium/land`, `/api/medium/temperature` | `regId`를 직접 전달하는 중기예보 조회 |

카탈로그의 의류·세트·소품 및 화면 템플릿도 유지했습니다.

## 실행과 검증

기존 JDK 21 / Spring Boot 4.1.1 / Gradle Wrapper 9.7.1 설정을 유지합니다.
MariaDB 연결과 기상청 인증 설정도 원본과 같습니다.

```bash
./gradlew test bootJar
./gradlew bootRun
```

Windows에서는 `gradlew.bat test bootJar`, `gradlew.bat bootRun`을 사용하세요.
테스트는 H2를 사용하며 자동·수동 수집을 기본 비활성화합니다. 수동 API 테스트만 수동 설정을 켜고 기상청 응답을 mock으로 대체합니다.

이번 정리 내용과 실제 검증 범위는 [`docs/CLEANUP.md`](docs/CLEANUP.md)에 기록했습니다.
