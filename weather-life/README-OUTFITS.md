# Weather Life · 옷 추천 기능 통합본

첨부한 Weather Life 프로젝트에 날씨별 옷 추천을 넣었습니다. 기존 `APIController`의 `/api` 아래에서 호출합니다.
의류 170종을 JSON 파일로 관리하며, 추천 계산에 외부 AI API·학습 데이터·DB가 필요하지 않습니다.
프로젝트 자체의 기존 MariaDB 설정은 별개이며, 일반 실행 시 기존 DB 접속 환경이 필요합니다.

## 1. 실행하기

원본과 같은 **Spring Boot 4.1.1 · JDK 21 · Gradle Wrapper 9.7.1**을 사용합니다.
IntelliJ에서는 이 폴더의 `build.gradle`을 열고 **Project SDK와 Gradle JVM을 JDK 21**로 선택하세요.
기존 `WeatherLifeApplication`을 실행하면 추가한 기능도 함께 올라옵니다.

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

macOS / Linux:

```bash
chmod +x gradlew
./gradlew bootRun
```

첫 실행 시 Gradle·의존성을 내려받으므로 인터넷이 필요합니다. 추천 자체는 로컬 JSON으로 계산합니다.
기존 `application.properties`의 DB·기상청 설정을 사용합니다.

테스트 및 실행 JAR 생성:

```bash
./gradlew clean test bootJar
java -jar build/libs/weatherlife-0.0.1-SNAPSHOT.jar
```

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다.
테스트는 `src/test/resources/application.properties`의 H2 메모리 DB와 테스트용 인증값을 사용합니다.
추천 API 테스트의 기존 날씨 서비스 호출은 mock이므로 실제 기상청에 요청하지 않습니다.

## 2. 바로 API 호출하기

`POST http://localhost:8080/api/outfits/recommend`

```json
{
  "temperature": 24,
  "humidity": 65,
  "windSpeed": 2,
  "sky": "SUNNY",
  "precipitation": "NONE",
  "gender": "FEMALE",
  "occasion": "DAILY",
  "preference": "ONE_PIECE",
  "minTemperature": 14,
  "maxTemperature": 26,
  "count": 3,
  "seed": 0
}
```

최소 요청은 `{"temperature":24,"humidity":65}`입니다. Postman에서 Body → raw → JSON으로 넣으면 됩니다.
IDE의 HTTP Client / REST Client 확장을 사용한다면 `outfit-requests.http`에서 여러 예제를 바로 실행할 수 있습니다.

PowerShell로 요청하기:

```powershell
$outfitBody = @{ temperature = 24; humidity = 65; gender = 'FEMALE'; preference = 'PANTS' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/outfits/recommend' -ContentType 'application/json; charset=utf-8' -Body $outfitBody
```

## 3. 입력값

| 필드 | 값 / 단위 | 기본값 |
|---|---|---|
| temperature | 기온 ℃, -50~50 | **필수** |
| humidity | 습도 %, 0~100 | **필수** |
| windSpeed | 풍속 m/s, 0~60 | 0 |
| sky | SUNNY / PARTLY_CLOUDY / CLOUDY | CLOUDY |
| precipitation | NONE / RAIN / SNOW / MIXED | NONE |
| gender | MALE / FEMALE / UNISEX | UNISEX |
| occasion | DAILY / OFFICE / EXERCISE / HOME | DAILY |
| preference | ANY / PANTS / SKIRT / ONE_PIECE | ANY |
| thermalPreference | NORMAL / FEEL_COLD / FEEL_HOT | NORMAL |
| minTemperature | 외출 시간대 최저기온 ℃ | 미입력 |
| maxTemperature | 외출 시간대 최고기온 ℃ | 미입력 |
| count | 원하는 코디 수, 1~5 | 3 |
| seed | 임의의 코디 선택값. 생략하면 요청마다 난수로 설정되며, 같은 코디를 재현하려면 숫자를 명시하세요. | 랜덤 |

- enum은 **대문자**입니다. 필드명 오타와 잘못된 enum은 HTTP 400입니다.
- `UNISEX`는 남녀 모든 카탈로그를 무작위로 섞는 값이 아니라 **공통 의류만** 고르는 값입니다.
- `FEMALE + PANTS`이면 여성용·공통 바지 코디만 나옵니다. 여성복이라고 치마를 강제하지 않습니다.
- `PANTS`는 긴바지와 반바지 모두를 허용합니다. 길이는 기온 범위에 따라 결정합니다.
- `ONE_PIECE`는 원피스뿐 아니라 점프수트·멜빵바지·멜빵치마를 포함하는 **올인원 의류** 선호입니다.
- `OFFICE`는 비교적 단정한 의류에 한정했습니다. 실제 직장의 복장 규정은 따로 적용하세요.
- `EXERCISE`는 가벼운 야외 운동 기준입니다. 운동 강도별 최적화나 수영복 추천은 구현하지 않았습니다.
- `HOME`일 때 `temperature`는 **실내 온도**입니다. 실외의 비·바람·일교차·햇빛 보정을 사용하지 않으며 신발과 외투가 나오지 않습니다.
- 최저·최고기온을 입력하면 `최저 ≤ temperature ≤ 최고`를 만족해야 합니다.
- 동일한 입력·seed·카탈로그에는 같은 결과가 나옵니다. seed를 바꿔도 후보가 적으면 같은 결과가 나올 수 있습니다.

## 4. 결과 읽는 방법

`outfits` 배열의 각 원소가 **서로 독립된 코디 한 벌**입니다. 배열 원소끼리 옷을 합치지 마세요.

| 필드 | 의미 |
|---|---|
| recommendationType | RULE_BASED: 규칙 기반임을 표시 |
| catalogVersion | 사용한 JSON 카탈로그 버전 |
| input | 기본값까지 채운 요청 |
| selectionTemperature | 옷을 고르기 위한 서비스 내부 보정값. **공식 체감온도 아님** |
| outerSelectionTemperature | 겉옷을 고를 때 사용한 값. 최저기온이 있으면 이를 반영 |
| outfits[].top / bottom | 일반 상의·하의. 각 1개 또는 null |
| outfits[].onePiece | 원피스/점프수트/멜빵옷. 선택되면 일반 top·bottom은 null |
| outfits[].innerTop | 뷔스티에·니트 조끼·멜빵옷처럼 이너가 필요한 의류의 상의 |
| outfits[].outer | 겉옷 1개 또는 null |
| outfits[].outerUsage | WEAR: 착용 / CARRY: 챙기기 / NONE: 없음 |
| outfits[].baseLayer | 추울 때 덧입는 발열 내의 상하의 또는 null |
| outfits[].shoes | 신발 1개. HOME에서는 null |
| outfits[].accessories | 우산·모자·목도리·장갑·보온 스타킹 등의 문자열 목록 |
| outfits[].reasons | 해당 조합의 간단한 추천 이유 |
| advice / warnings | 날씨 안내 및 후보 부족·극단적인 날씨 안내 |

의류 객체에는 `id`, `name`, `slot`이 들어 있습니다. 화면에는 `name`을 출력하고 이미지 연결에는 `id`를 사용할 수 있습니다.
이미지 파일과 상품 링크는 포함하지 않았습니다. null인 슬롯은 화면에서 숨기면 됩니다.
실제 실행 결과 예시는 `docs/outfits/examples/response-*.json`을 참고하세요.

## 5. 잘못된 조합을 막는 방식

1. **일반 코디:** 상의 1개 + 하의 1개 + 필요한 겉옷/신발/내의.
2. **원피스 코디:** 원피스류 1개 + 필요한 겉옷/신발/내의. 일반 상의·하의는 없음.
3. **레이어드 원피스:** 멜빵옷처럼 `requiresInnerTop=true`이면 `innerTop`만 별도로 추가.
4. 바지와 치마는 같은 `BOTTOM` 슬롯을 사용하므로 동시에 선택될 수 없음.
5. 가디건·후드 집업은 `OUTER`에만 등록해 상의와 겉옷으로 중복 선택되지 않음.
6. 같은 의류 ID가 한 코디에 중복되지 않음. 신발만 다른 대안은 별도 코디로 세지 않음.
7. 이너가 필요한 옷을 단독으로 내보내지 않음. 대응하는 이너가 없으면 그 후보는 버림.
8. 강풍에는 `WIND_SENSITIVE`, 비에는 `AVOID_RAIN` 항목을 제외.
9. 비·눈에는 겉옷과 신발의 `waterResistance >= 1`, 눈·진눈깨비에는 신발의 `GRIP` 태그가 필요.
10. 조건을 만족하는 후보가 없으면 **422**를 반환. 선호를 무시하거나 계절이 맞지 않는 옷을 억지로 채우지 않음.

**색상·패턴·실제 상품 사진의 미적 궁합까지 판단하는 기능은 아닙니다.** 구조·계절·용도 및 기본 스타일을 맞추는 기능입니다.

## 6. 추천 로직을 수정할 위치

| 파일 | 담당 |
|---|---|
| `src/main/resources/data/clothing-catalog.json` | 의류 종류·추천 기온·통풍성·방수/방풍·성별·용도 |
| `src/main/java/com/weatherlife/service/WeatherRules.java` | 습도·햇빛·바람·추위 민감도·일교차 보정, 겉옷/내의 기준 |
| `src/main/java/com/weatherlife/service/OutfitService.java` | 후보 필터, 점수 계산, 한 벌 조합, 대안 선택 |
| `src/main/java/com/weatherlife/dto/OutfitTypes.java` | enum 목록 |
| `src/main/java/com/weatherlife/dto/OutfitRequest.java` | 입력 필드·범위·기본값 |
| `src/main/java/com/weatherlife/dto/OutfitResponse.java` | 응답 형식 |
| `src/main/java/com/weatherlife/controller/APIController.java` | REST API 주소 |

현재 보정 예: 더운 날 높은 습도는 최대 +2, 따뜻하고 맑으면 +1, 쌀쌀한 날 바람은 최대 -4,
추위를 많이 타면 -2, 더위를 많이 타면 +2, 야외 운동은 +3으로 추천 기준을 조정합니다.
이는 구현을 시작하기 위한 **수작업 경험 규칙**입니다. 검증된 체감온도 공식이나 학습 모델이 아닙니다.
기본 선택 기준이 20℃ 미만이면 겉옷, 5℃ 이하이면 내의를 고려하고, 강수/강풍이면 별도 겉옷 조건을 적용합니다.

점수는 적정 기온과의 거리 + 소재 특성 + 스타일 일치 정도를 조합합니다. 실제 확률이 아니므로 응답에 정확도처럼 표시하지 않습니다.

## 7. 의류를 추가하는 방법

JSON의 `items` 배열에 같은 형태의 객체를 추가합니다. 기존 `id`는 이미지 매핑 등이 있으면 변경하지 마세요.

```json
{
  "id": "CUSTOM001",
  "name": "추가한 얇은 셔츠",
  "slot": "TOP",
  "genders": ["MALE", "FEMALE", "UNISEX"],
  "occasions": ["DAILY", "OFFICE"],
  "style": "SMART",
  "minTemperature": 18,
  "maxTemperature": 32,
  "idealTemperature": 25,
  "breathability": 4,
  "waterResistance": 0,
  "windResistance": 0,
  "bottomType": "NONE",
  "requiresInnerTop": false,
  "tags": ["INNER_OK"]
}
```

- `style`: CASUAL / SMART / SPORT / HOME. 스타일 차이는 감점이며 절대 제외 조건은 아닙니다.
- `breathability`: 0~5. 값이 클수록 통풍성이 좋다고 취급합니다.
- `waterResistance`, `windResistance`: 0~2. 0은 없음, 1은 약한 기능, 2는 높은 기능.
- 일반 패딩/가죽/플리스를 방수라고 가정하지 않았습니다. 비 대응용은 별도 기능성 품목입니다.
- `bottomType`: 하의는 PANTS / SHORTS / SKIRT, 그 외는 NONE.
- `tags`: INNER_OK(이너로 가능), WIND_SENSITIVE(강풍에 제외), AVOID_RAIN(강수에 제외), GRIP(접지력).
- `QUICK_DRY`는 확장용 메타데이터입니다. 현재 직접 점수에 사용하지 않으며 통풍성 값으로 반영합니다.
- 추천 기온 범위의 양 끝값은 포함합니다. `idealTemperature`도 범위 안에 있어야 합니다.
- 일반 속옷·수영복은 제외했습니다. 발열 내의는 추운 날의 보온 레이어로 포함했습니다.
- JSON의 중복 ID·잘못된 값은 시작 시 오류로 알려줍니다.

파일은 시작할 때 한 번 읽으므로 변경 후 재시작합니다.
JAR를 매번 다시 만들기 싫다면 외부 JSON을 읽도록 지정할 수 있습니다.

```bash
java -jar build/libs/weatherlife-0.0.1-SNAPSHOT.jar --outfit.catalog-location=file:./config/clothing-catalog.json
```

## 8. 이번에 통합한 위치

| 파일 | 변경 내용 |
|---|---|
| `controller/APIController.java` | 생성자 주입 필드 2개와 `POST /outfits/recommend`, `GET /outfits/catalog` 추가 |
| `controller/OutfitExceptionHandler.java` | 입력 오류 400 / 추천 후보 없음 422 응답 |
| `service/OutfitService.java` | 한 벌 조합 및 추천 순위 |
| `service/WeatherRules.java` | 온도·습도·햇빛·비·바람 해석 |
| `service/ClothingCatalog.java` | Jackson 3 `JsonMapper`로 의류 JSON 읽기 |
| `service/NoOutfitException.java` | 조건에 맞는 코디가 없을 때 쓰는 예외 |
| `dto/OutfitRequest.java`, `dto/OutfitResponse.java` | API 요청·응답 자료형 |
| `dto/Garment.java`, `dto/OutfitTypes.java` | 의류 메타데이터와 enum |
| `src/main/resources/data/clothing-catalog.json` | 의류 170종 |
| `build.gradle` | 테스트 전용 Web MVC starter 및 H2 추가 |
| `src/test/java/com/weatherlife/OutfitApiTest.java` | 추천 규칙·입력 오류·기존 날씨 경로 테스트 |
| `src/test/resources/` | 테스트 DB 설정 및 Mockito 설정 |

표의 Java 파일은 `src/main/java/com/weatherlife/` 기준입니다.
`@RequiredArgsConstructor`가 추가한 `final` 필드의 생성자 주입을 처리합니다.
컨트롤러의 클래스 수준 `/api`와 메서드 수준 `/outfits/recommend`가 합쳐져 최종 주소가 됩니다.
이전 예제의 독립 애플리케이션과 `OutfitController`는 넣지 않았습니다.

원본의 Spring Boot/Java/Gradle 버전, 날씨 서비스, 지역 CSV, 템플릿, 운영 설정을 유지했습니다.
기존 `/api/test`, `/api/short`, `/api/medium/land`, `/api/medium/temperature`를 그대로 사용합니다.
원본 ZIP의 오래된 빌드 결과와 Gradle 캐시는 소스 배포 ZIP에서 제외했습니다. 실행 시 Gradle이 빌드 결과를 다시 만듭니다.

### 화면에서 호출하기

현재 통합 범위는 백엔드 API입니다. 기존 대시보드에 옷 추천 UI를 자동으로 삽입하지는 않았습니다.
같은 서버의 프런트엔드에서는 다음처럼 호출할 수 있습니다.

```javascript
const response = await fetch('/api/outfits/recommend', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    temperature: 24,
    humidity: 65,
    sky: 'SUNNY',
    precipitation: 'NONE',
    gender: 'FEMALE',
    preference: 'PANTS'
  })
});
const result = await response.json();
if (!response.ok) throw new Error(result.message ?? '추천 요청 실패');
console.log(result.outfits); // 각 원소가 하나의 완성된 코디입니다.
```

기상청 응답을 서버에서 자동으로 해석해 추천하는 연결은 별도입니다. 이 API에는 사용할 시간대의 기온·습도 등을 숫자로 넘겨주세요.
Thymeleaf 컨트롤러 등 서버 내부에서는 `OutfitService`를 생성자 주입받아 `recommend(OutfitRequest)`를 직접 호출해도 됩니다.

## 9. 오류 응답

| HTTP | code | 의미 |
|---|---|---|
| 400 | INVALID_REQUEST | 필수값 누락, 범위 초과, 잘못된 최저/최고기온 |
| 400 | INVALID_JSON | JSON 문법·필드명·enum·숫자 형식 오류 |
| 422 | NO_MATCHING_OUTFIT | 성별·용도·선호·날씨를 모두 만족하는 조합이 없음 |

`count`보다 적은 후보만 있으면 가능한 후보와 `warnings`를 HTTP 200으로 반환합니다.
추천 DTO의 알 수 없는 필드만 거절하며 기존 API의 전역 Jackson 설정을 바꾸지 않습니다.
의류 데이터 파일 손상은 시작 시 오류로 알려줍니다.

## 10. 참고 및 검증

카탈로그 수치와 추천 규칙은 사용자의 의류 분류를 바탕으로 사람이 정한 초깃값입니다.
실제 착용 만족도를 측정한 데이터셋이나 학습 모델은 아닙니다.

호환성 확인에 사용한 공식 문서:

- https://docs.spring.io/spring-boot/4.1/reference/features/json.html
- https://docs.spring.io/spring-boot/4.1/reference/testing/spring-boot-applications.html

이번 통합본의 검증 범위는 `docs/outfits/VERIFICATION.md`에 기록합니다.

## 6. 시간별 기상청 단기예보 → 옷 추천 (신규)

`GET /api/outfits/today?regionName=서울특별시 영등포구 신길제1동`

기상청 원본 데이터를 **오늘 날짜의 시간별 DTO**로 변환하고 시간순 Map에 담아 코디를 반환합니다.
연결 구조·JSON 예시는 [`docs/HOURLY_OUTFIT_API.md`](docs/HOURLY_OUTFIT_API.md)를 참고하세요.
