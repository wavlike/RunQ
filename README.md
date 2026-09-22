# RunQ

강릉을 달리는 사람들을 위한 러닝 코스 · 장소 추천 안드로이드 앱.
"어디를 달릴지"뿐 아니라 "달리고 나서 어디서 쉴지"까지 이어주는 것을 목표로 한다.
강릉 지역 러닝 코스를 큐레이션해서 추천하고, 실시간 GPS로 러닝을 기록하며,
완주 지점(Finish Hub) 주변의 맛집·카페·볼거리를 실제 API 데이터로 안내한다.

- ONE store(원스토어) 배포
- 패키지명: `com.runq.app`

---

## 목차
1. [주요 기능](#주요-기능)
2. [기술 스택](#기술-스택)
3. [연동한 외부 API](#연동한-외부-api)
4. [프로젝트 구조](#프로젝트-구조)
5. [콘텐츠 데이터 구조](#콘텐츠-데이터-구조)
6. [핵심 설계 원칙](#핵심-설계-원칙)
7. [빌드 방법](#빌드-방법)
8. [담당 역할](#담당-역할)

---

## 주요 기능

### 1. 코스(Course) 추천 및 탐색
- 강릉 전역 38개 큐레이션 러닝 코스 제공 (거리/난이도/지형/교통량/경관 태그로 필터·정렬)
- 코스 상세: 지도 위 실제 경로(RouteLine), 시작/도착 지점, 예상 소요시간, 코스 특징
- 검색 화면에서 코스/장소 통합 검색

### 2. 실시간 GPS 러닝 기록
- **코스 기반 러닝**: 코스를 선택해 "다음 러닝으로 설정"하면 Run 탭에서 해당 경로를 따라가며 진행률(코스 위 누적 거리) 표시
- **자유 러닝**: 코스 없이 Run 탭에서 바로 GPS 기반 거리·페이스·시간·칼로리 측정
- `FusedLocationProviderClient`로 실시간 위치 수집, 두 지점 간 거리는 Haversine 공식으로 직접 계산
- GPS 신호를 10초 이상 못 잡으면 안내 모달 표시, 최초 실행 시 위치 권한이 왜 필요한지 설명하는 화면 선행
- 러닝 종료 시 실제 그 시각의 날씨(기온·미세먼지)까지 함께 기록에 저장
- 완주 시 거리/시간/평균 페이스/소모 칼로리(㎞당 65kcal 추정치) 요약 화면 제공, 코스 완주 시엔 해당 코스의 Finish Hub 바로가기까지 노출

### 3. Finish Hub — 완주 후 주변 정보
- 강릉 전역을 18개 구역(Finish Hub)으로 나눠, 완주 지점 기준으로 반경 내 EAT/CAFE/SEE/행사(EVENT) 정보 제공
- 자체 큐레이션 데이터(151곳)를 우선 노출하고, 부족한 부분은 TourAPI·Kakao Local API로 실시간 보강해 중복 없이 병합
- 기본 정렬은 "추천순"(사진·소개문구가 채워진 곳 우선) — 단순 거리순보다 실제로 도움이 되는 곳을 앞에 노출
- 장소 상세: 주소/전화/운영시간/사진/설명을 큐레이션 데이터 → TourAPI → Kakao Local 순으로 우선순위를 두고 채움 (큐레이션 데이터가 항상 최우선, 자동 매칭 데이터로 덮어쓰지 않음)
- 큐레이션 장소에 TourAPI contentId가 없어도, 이름 기반 키워드 검색(`searchKeyword2`)으로 확실히 같은 장소일 때만 상세정보를 보강 (오매칭 방지를 위해 제목 포함관계 검증)
- 지도에는 카테고리별(E/C/S) 글자 배지 마커로 구분 표시
- "여기로 달리기" 버튼으로 현재 위치 → 목적지까지 카카오맵 앱의 실제 도보 경로 안내 연동
- 강릉 행사/축제 정보를 TourAPI에서 실시간으로 가져오고, 진행중/예정/마감 상태를 직접 계산해 표시. 팀이 직접 확인한 행사(예: 강릉커피축제)는 큐레이션 데이터로 등록해 API 결과보다 우선 노출

### 4. 실시간 날씨 · 러닝 적합도
- 기상청 초단기실황/초단기예보 API로 실제 기온·강수·바람·하늘상태(맑음/흐림/비/눈 등)를 계산
- 에어코리아 API로 강릉 지역 실시간 미세먼지 등급 조회
- 두 데이터를 종합해 "오늘 러닝 적합도"(우천 주의/대기질 주의/강풍 주의/좋음)를 규칙 기반으로 판정
- 코스별 날씨는 코스에 지정된 좌표(격자)를 기준으로 조회

### 5. 알림
- 서버 푸시 없이, 기기에 저장된 실제 데이터(러닝 기록/저장 목록/추천 코스)만으로 생성되는 로컬 알림
- 오늘/어제 날짜별 그룹핑, 안읽음 표시(점 + 홈 화면 벨 아이콘 배지)
- 알림 탭 시 관련 화면(코스 상세/저장 목록/러닝 기록/Finish Hub)으로 바로 이동
- "Finish Hub 추천": 방금 완주한 코스의 Finish Hub 기준 추천 카페 안내
- "주간 러닝 리포트": 이번 주 실제 누적 거리·평균 페이스를 지난주와 비교

### 6. My — 기록 · 저장 · 프로필
- 러닝 기록 목록/상세(거리, 시간, 페이스, 당시 날씨, 지도)
- 저장한 코스/장소 목록 (하트로 저장, 탭하면 바로 상세로 이동)
- 프로필 사진 변경(Android Photo Picker, 런타임 권한 불필요)
- 위치 권한 설정 바로가기, 계정 삭제 등 설정 메뉴

### 7. 회원가입 · 로그인
- 자체 회원가입/로그인 화면 (카카오 로그인 등 소셜 로그인 미사용)
- 개인정보 수집 동의 체크박스를 회원가입 폼 진입 **이전**에 필수로 통과하도록 게이팅

---

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose (Material 3) |
| 아키텍처 | 단일 Activity + Compose Navigation(자체 sealed class 기반 상태 전환), 상태 관리는 `remember`/`mutableStateOf` 중심 |
| 비동기 | Kotlin Coroutines |
| 네트워킹 | Retrofit2 + Gson Converter |
| 이미지 로딩 | Coil |
| 위치 | Google Play Services Location (`FusedLocationProviderClient`) |
| 지도 | Kakao Maps SDK v2 (Vector Map) |
| 로컬 저장소 | SharedPreferences + Gson (자체 백엔드 없이 기기 로컬에 저장) |
| 데이터 소스 | 앱 내장 JSON(assets) — 자체 큐레이션 DB |
| 빌드 | Gradle (Kotlin DSL), Android Gradle Plugin |
| 배포 | ONE store |

---

## 연동한 외부 API

### 1. Kakao Maps SDK (지도 표시)
- 코스 경로(RouteLine), 시작/도착 지점, 현재 위치, Finish Hub 주변 장소 마커 렌더링
- `KakaoMapSdk.init()`을 앱 시작 시 1회 초기화 (`RunQApplication`)
- 네이티브 앱 키 기반 인증이며, 배포 환경(ONE store 자체 서명 등)에 따라 서명 인증서별 키 해시를 Kakao Developers 콘솔에 별도 등록해야 동작

### 2. Kakao Local API (REST)
- 키워드/카테고리 기반 장소 검색으로 카페 정보 보강 (`category_group_code=CE7`)
- 장소의 실제 전화번호, 카테고리명(`category_name`)에서 파생한 태그, 카카오맵 상세 페이지 링크 제공
- REST API 키 사용 (Kakao Maps SDK의 네이티브 앱 키와는 별개)

### 3. 한국관광공사 TourAPI (KorService2)
- `locationBasedList2`: Finish Hub 반경 내 음식점/관광지/문화시설/레포츠 실시간 조회
- `detailCommon2`: 장소 상세 설명(overview), 대표 이미지, 전화번호, 주소
- `detailIntro2`: 콘텐츠타입별 운영시간·휴무일 (음식점/관광지/문화시설/레포츠/쇼핑마다 필드가 달라, 응답의 실제 콘텐츠타입 기준으로 올바른 필드를 선택해서 읽음)
- `searchKeyword2`: 큐레이션 장소 중 contentId가 없는 곳을 이름으로 검색해 상세정보 보강 (제목 포함관계 검증으로 오매칭 방지)
- `searchFestival2`: 강릉 지역 진행중/예정 행사·축제 목록

### 4. 기상청 단기예보 (VilageFcstInfoService_2.0)
- `getUltraSrtNcst`(초단기실황): 실시간 기온, 강수량, 풍속
- `getUltraSrtFcst`(초단기예보): 하늘상태(SKY)·강수형태(PTY) — 실황 API엔 없는 정보라 예보 API를 별도 조회해 "맑음/구름많음/흐림/비/눈/소나기"를 실제 값으로 판정

### 5. 에어코리아 대기오염정보 (ArpltnInforInqireSvc)
- `getCtprvnRltmMesureDnsty`: 강원 지역 실시간 미세먼지(PM10/PM2.5) 등급

> 모든 외부 API 인증키는 소스코드에 하드코딩하지 않고 `local.properties`(버전관리 제외) → `BuildConfig` 필드로 주입해서 사용한다.

---

## 프로젝트 구조

```
app/src/main/java/com/example/runq/
├── MainActivity.kt         # 앱 진입점, 하단 탭 구조, 화면 간 상태(Tab/CourseStep 등) 관리
├── AuthScreens.kt           # 로그인/회원가입/약관 동의 플로우
├── RunQScreens.kt           # Home, 검색, 알림, Run(자유러닝) 화면
├── FinishHubScreens.kt      # Course 상세/RunReady/러닝/완주, Place 목록/상세, Finish Hub 로직
├── MyScreens.kt             # My 탭(기록/저장/설정/프로필)
├── SystemStates.kt          # 공용 Empty/Error/Permission 상태 화면(마스코트 일러스트 포함)
├── KakaoMapView.kt          # Kakao Maps SDK 래퍼 Composable (경로/마커 렌더링)
├── KakaoLocalService.kt     # Kakao Local REST API
├── TourApiService.kt / TourApiModels.kt   # 한국관광공사 TourAPI
├── SafetyApiService.kt      # 기상청 + 에어코리아 API, 러닝 적합도 판정
├── RunQData.kt              # assets/data/*.json 로더 (JSON → 도메인 모델)
├── FinishHub.kt             # Course/FinishHub 도메인 모델, 거리 계산 확장 함수
├── LocalStore.kt            # SharedPreferences 기반 로컬 저장소 (러닝기록/저장목록/알림 읽음상태 등)
├── GeoUtils.kt              # 좌표 거리 계산(Haversine) 등 지리 유틸
├── RunQEnums.kt             # 콘텐츠 상태/난이도/지형 등 enum 및 파서
├── RunQTheme.kt             # 색상/타이포그래피 등 디자인 토큰
└── RunQApplication.kt       # Application 클래스, Kakao SDK 초기화

app/src/main/assets/data/
├── courses.json             # 큐레이션 러닝 코스 (38개)
├── finish_hubs.json         # Finish Hub 구역 정의 (18개)
├── places.json              # 큐레이션 장소 (EAT/CAFE/SEE, 151곳)
├── festivals.json           # 팀이 직접 확인한 행사 정보 (API 보강용)
└── route_points/            # 코스별 실제 경로 좌표(GPS 포인트 배열)
```

---

## 콘텐츠 데이터 구조

RunQ는 서버/백엔드 없이 **자체 큐레이션 JSON + 외부 API 실시간 조회**를 조합하는 구조다.

- 팀이 직접 답사·확인한 코스/장소/행사는 `assets/data/*.json`에 실려 항상 우선 노출됨(`isCurated = true`)
- 큐레이션 데이터가 비어있는 필드(운영시간, 상세설명, 사진 등)만 TourAPI/Kakao Local로 보강
- API로만 채워지는 정보(예: 큐레이션에 없는 신규 맛집)는 자동으로 병합하되, 이름이 겹치면 큐레이션 쪽을 남기고 중복 제거
- **없는 데이터를 임의로 만들어내지 않는다는 원칙**을 전 구간에 일관되게 적용 — 예를 들어 실제 API로 확인 불가능한 별점/리뷰/키워드 태그는 표시하지 않고, 하늘 상태처럼 실제 API 필드가 없으면 임의로 "맑음" 등을 채우지 않고 "확인중"으로 표시

---

## 핵심 설계 원칙

이 프로젝트를 진행하면서 지킨 원칙들:

1. **가짜 데이터 금지** — API로 검증되지 않은 정보(별점, 리뷰, 임의 태그, 날씨 하드코딩 등)는 절대 표시하지 않는다. 정보가 없으면 "확인중"/"정보 없음"으로 명시한다.
2. **큐레이션 데이터 우선** — 사람이 직접 확인한 정보가 있으면 API 자동 매칭 결과보다 항상 우선한다(사진, 주소, 전화번호, 설명 등). 자동 매칭은 빈 곳만 보강한다.
3. **실제 동작하는 인터랙션만 남긴다** — 디자인상 존재하지만 실제로는 아무 동작도 하지 않는 버튼("죽어있는 버튼")을 지속적으로 찾아 실제 네비게이션/기능으로 연결했다.
4. **탭 간 상태 전달은 요청/소비(Request-Consume) 싱글턴 패턴** — 하단 탭 전환 시 이전 화면의 Compose 상태가 사라지는 구조적 특성 때문에, 특정 코스/장소를 다른 탭에서 열어야 할 때는 `CourseTabRequest`/`PlaceTabRequest`/`MyTabRequest` 같은 요청 객체를 통해 목적지 화면에 필요한 정보를 전달한다.

---

## 빌드 방법

1. 저장소를 클론한다.
2. 프로젝트 루트에 `local.properties`를 만들고 아래 키를 채운다.
   ```properties
   KAKAO_NATIVE_APP_KEY=...   # Kakao Maps SDK 지도 표시용
   KAKAO_REST_API_KEY=...     # Kakao Local API(장소 검색)용
   TOUR_API_KEY=...           # 한국관광공사 TourAPI / 기상청 / 에어코리아 공용 서비스키
   ```
3. Kakao Developers 콘솔에서 해당 앱에 대해:
   - **카카오맵** 제품을 활성화(사용 설정 ON)
   - Android 플랫폼에 패키지명(`com.runq.app`)과 실제 서명에 사용하는 인증서의 키 해시를 등록 (로컬 디버그 키, 릴리즈 서명키, 배포 플랫폼의 업로드키 등 실제 사용하는 서명 경로별로 모두 등록 필요)
4. Android Studio에서 Gradle Sync 후 실행, 또는 `./gradlew assembleRelease`(서명 설정 시)로 빌드.

---

## 담당 역할

- **기획 및 콘텐츠 큐레이션**: 강릉 지역 러닝 코스/Finish Hub 구역/맛집·카페·볼거리를 직접 조사해 데이터베이스(엑셀→JSON) 구축
- **디자인**: Figma로 전체 화면(홈/코스/러닝/Place/My/시스템 상태 화면 등) 디자인 및 마스코트 일러스트 방향 설정
- **기능 정의 및 우선순위 결정**: 어떤 데이터를 API로 실시간 연동하고 어떤 데이터를 직접 큐레이션할지, 가짜 데이터 없이 구현 가능한 범위가 어디까지인지 매 기능마다 판단
- **API 연동 전략 수립**: Kakao Maps/Local, 한국관광공사 TourAPI, 기상청, 에어코리아 등 여러 공공/민간 API를 조사해 앱에 필요한 정보(운영시간, 날씨, 하늘상태 등)를 어떤 API 조합으로 채울 수 있는지 확인하고 연동 방향 결정
- **QA 및 실기기 테스트**: 실제 기기·ONE store 배포본에서 지속적으로 테스트하며 버그(죽어있는 버튼, 하드코딩된 가짜 데이터, 지도 렌더링 실패 등)를 발견하고 재현 조건을 좁혀 원인 파악
- **배포 운영**: ONE store 콘솔을 통한 앱 등록/버전 관리/스토어 심사 대응, Kakao Developers 콘솔의 플랫폼 등록·키 해시·카카오맵 활성화 등 서비스 설정 관리
- **구현**: Claude Code(AI 페어 프로그래밍)를 활용해 위 기획·설계 방향에 따라 Kotlin/Jetpack Compose 코드로 직접 구현을 진행 — 매 기능마다 실제 데이터 흐름을 검증하고, 가짜 데이터나 죽어있는 버튼이 발견되면 즉시 실제 동작으로 수정하는 과정을 반복

> 위 "담당 역할" 항목은 실제 이력에 맞게 자유롭게 수정해서 사용하면 됩니다.
