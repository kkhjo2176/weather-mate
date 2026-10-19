# weather-life Docker 실행 가이드

Spring Boot 앱(`weather-life`)과 MariaDB를 Docker로 함께 실행하는 방법을 정리했다.
로컬에 MariaDB를 직접 설치하지 않고도 `docker compose` 한 번으로 앱과 DB를 같이 띄울 수 있다.

## 1. 실행하기 전에 알아둘 내용

| 이름 | 역할 |
| --- | --- |
| Dockerfile | Gradle로 앱을 빌드하고, 빌드된 jar를 실행하는 방법을 적은 파일 |
| compose.yaml | 앱 컨테이너와 DB 컨테이너, 포트, 환경변수를 한 번에 설정하는 파일 |
| .env | 비밀번호·API 키 등 실제 값을 담는 파일. git에는 올리지 않는다 |

관련 파일 위치는 다음과 같다 (모두 `weather-life/weather-life` 폴더, 즉 `build.gradle`이 있는 폴더).

```text
weather-life/
├─ Dockerfile
├─ compose.yaml
├─ .dockerignore
├─ .env.example
├─ DOCKER_RUN_GUIDE.md  ← 현재 문서
├─ build.gradle
└─ src/
```

## 2. ⚠️ 먼저 확인할 것: 기존 application.properties의 비밀값

`src/main/resources/application.properties`에 DB 비밀번호와 기상청 API 키가 **평문으로 들어 있고 이미 git에 커밋되어 있다.**

```properties
spring.datasource.username=human
spring.datasource.password=human
kma.short.auth-key=...
kma.medium.service-key=...
ai.forecast.api-key=...
```

이 가이드의 compose 설정은 이 값들을 **환경변수로 덮어써서** 실제 비밀값이 파일에 남지 않도록 했다.
다만 기존 파일의 값 자체가 이미 저장소 히스토리에 남아 있으므로, 공개 저장소라면 키 재발급(rotate)을 권장한다.
이 부분은 이번 작업 범위를 벗어나므로 직접 손대지 않았고, 필요하면 알려달라.

## 3. .env 파일 준비

Docker Desktop을 켜고, 이 폴더(`weather-life/weather-life`)에서 작업한다.

```powershell
cd D:\sjtool\workspace\team-project2\weather-life\weather-life
Copy-Item .env.example .env
```

`.env` 파일을 열어 아래 값을 채운다.

| 변수 | 의미 |
| --- | --- |
| `DB_NAME` | 사용할 데이터베이스 이름 (기본값 `weather`) |
| `DB_USER` / `DB_PASSWORD` | 앱이 DB에 접속할 계정 |
| `DB_ROOT_PASSWORD` | MariaDB root 계정 비밀번호 |
| `DB_PORT` | 호스트에서 DB에 접속할 포트 (기본 `3306`) |
| `APP_PORT` | 호스트에서 앱에 접속할 포트 (기본 `8080`) |
| `KMA_SHORT_AUTH_KEY` 등 | 기존 `application.properties`에 있던 API 키들 |

`.env`는 `.gitignore`에 등록되어 있어 git에 올라가지 않는다.

## 4. 빌드 및 실행

```powershell
docker compose up -d --build
```

| 옵션 | 의미 |
| --- | --- |
| `up` | 컨테이너를 만들거나 실행한다 |
| `-d` | 백그라운드로 실행한다 |
| `--build` | Dockerfile을 기준으로 이미지를 새로 빌드한다 |

처음 실행할 때는 Gradle 의존성과 MariaDB 이미지를 내려받아 시간이 걸린다.
앱은 DB의 헬스체크(`healthy`)가 통과한 뒤에 시작하도록 설정되어 있다.

상태와 로그 확인:

```powershell
docker compose ps
docker compose logs --tail 50 app
docker compose logs --tail 50 db
```

`app` 로그에서 `Started WeatherLifeApplication`이 보이면 정상 기동된 것이다.
DB 스키마는 `spring.jpa.hibernate.ddl-auto=update` 설정으로 앱이 처음 뜰 때 자동 생성된다.

## 5. 접속 확인

```powershell
Invoke-RestMethod http://localhost:8080
```

브라우저에서 `http://localhost:8080`으로 대시보드 화면이 뜨는지 확인한다.
DB에 직접 접속해서 확인하려면:

```powershell
docker compose exec db mariadb -u $env:DB_USER -p $env:DB_NAME
```

## 6. 종료

```powershell
docker compose down
```

DB 데이터는 `db-data` 볼륨에 남아 있으므로, 다시 `docker compose up -d`로 실행하면 이전 데이터를 그대로 사용한다.
DB 데이터까지 완전히 초기화하려면:

```powershell
docker compose down -v
```

## 7. 자주 겪는 문제

| 증상 | 먼저 확인할 내용 |
| --- | --- |
| `app` 컨테이너가 DB 접속 오류로 재시작됨 | `docker compose ps`에서 `db`가 `healthy`인지, `.env`의 `DB_USER`/`DB_PASSWORD`가 compose 설정과 일치하는지 확인 |
| `Set DB_USER in your .env` 같은 오류 | `.env` 파일이 없거나 해당 변수가 비어 있음. 2번 항목대로 `.env`를 채웠는지 확인 |
| 포트가 이미 사용 중이라는 오류 | `.env`의 `APP_PORT` 또는 `DB_PORT`를 다른 값으로 바꾼 뒤 `docker compose up -d --build` 재실행 |
| 코드 수정이 반영되지 않음 | `--build` 옵션 없이 `up`을 실행했을 가능성. `docker compose up -d --build`로 다시 실행 |
| 기상청/AI 서버 호출이 실패함 | `.env`의 `KMA_*`, `AI_FORECAST_*` 값이 채워져 있는지, AI 서버(`ai-service`)가 해당 주소에서 실행 중인지 확인 |

## 참고 파일

- [Dockerfile](Dockerfile): 이미지 구성
- [compose.yaml](compose.yaml): 실행 설정
- [.env.example](.env.example): 환경변수 예시
