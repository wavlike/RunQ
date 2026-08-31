package com.example.runq

// ════════════════════════════════════════════════════════
// Finish Hub: 완주 지점(코스 종료 핀)이 속한 구역 데이터
//
// 코스 경로 자체는 RunQ DB(allCourses)에 저장하고, 완주 후 추천은
// "코스 중심 좌표"가 아니라 "코스가 속한 Finish Hub"의 좌표+반경 기준으로 조회한다.
//
//   완주 → course.finishHubIds → findHub() → hub.lat/lng + hub.radiusMeters
//        → TourAPI locationBasedList2 호출 → EAT / CAFE / SEE
//
// Course는 hub_id만 참조(FK)하고, 좌표·반경·큐레이션 리스트는 Hub 쪽에 한 번만
// 저장한다. 코스 row마다 hub의 lat/lng/radius를 중복 저장하지 않는 이유는
// Hub 반경이나 추천 리스트가 바뀔 때 코스 57개를 전부 고칠 필요가 없게 하기 위함.
// (프론트 쪽 예시 스키마는 course에 finish_hub_lat/lng까지 들고 있었는데,
//  그건 여기서는 hub_id 참조로 정규화했다.)
//
// hub.curatedEat/Cafe/See는 노션 "Finish Hub" 표에 정리된 RunQ 큐레이션 장소로,
// API 응답에서 이 이름과 일치하는 장소를 최상단에 고정 노출하기 위한 값이다.
// ════════════════════════════════════════════════════════

data class FinishHub(
    val id: String,                 // "A" ~ "R"
    val name: String,               // "강문·초당"
    val lat: Double,
    val lng: Double,
    val radiusLabel: String,        // 노션 원문 표기 ("1km", "2 ~ 4km" 등, 화면 노출용)
    val radiusMeters: Int,          // TourAPI radius 파라미터 값 (범위면 상한 기준)
    val curatedEat: List<String>,
    val curatedCafe: List<String>,
    val curatedSee: List<String>
)

// ⚠️ lat/lng는 Hub 대표 지명을 기준으로 잡은 "근사 좌표" 플레이스홀더입니다.
// 노션 "코스별 시작/종료 핀" 지도에 실제로 찍힌 좌표로 반드시 교체해주세요.
val finishHubs: List<FinishHub> = listOf(
    FinishHub("A", "강문·초당", 37.7936, 128.9163, "1km", 1000,
        listOf("하월당 초당짬뽕순두부", "고씨네동해막국수&순두부칼국수 본점", "강릉감자닭강정"),
        listOf("초당커피정미소 본점", "카페 툇마루", "말차로", "갤러리밥스", "꾸옥 GGUOK"),
        listOf("허균·허난설헌기념공원", "강문해변")),
    FinishHub("B", "안목", 37.7713, 128.9470, "800m", 800,
        listOf("안목반점", "리틀다이너", "미트컬쳐"),
        listOf("체크이스트", "AM에이엠", "보사노바 커피로스터"),
        listOf("안목해변", "릉항요트마리나")),
    FinishHub("C", "경포", 37.8046, 128.9086, "1.5km", 1500,
        listOf("최일순짬뽕순두부", "오봉이해물칼국수", "강문가"),
        listOf("테라로사 경포호수", "백야커피", "5 to 7"),
        listOf("경포해변", "아르떼뮤지엄 강릉")),
    FinishHub("D", "주문진·소돌", 37.8995, 128.8281, "1.5km", 1500,
        listOf("미미네집", "윤가네 조개구이", "소돌막국수", "파도식당", "바다이모카"),
        listOf("소돌엔", "사이드", "오엔카페"),
        listOf("소돌아들바위공원", "주문진항")),
    FinishHub("E", "영진", 37.8579, 128.8477, "1.5km ~ 2km", 2000,
        listOf("영진돈", "부영식당", "연곡꾹저구탕"),
        listOf("콩방앗간", "바우카페", "보헤미안 박이추 커피 본"),
        listOf("영진해변", "영진항")),
    FinishHub("F", "월화·중앙시장", 37.7519, 128.8971, "800m", 800,
        listOf("강릉 수제 어묵고로케", "감자바우 장칼국수", "강릉김밥", "오하이오", "여고시절 카레떡볶이"),
        listOf("리도커피", "만동제과 강릉점", "라스텔리"),
        listOf("월화거리·월화교", "강릉중앙시장")),
    FinishHub("G", "남항진", 37.7524, 128.9291, "1km", 1000,
        listOf("감자적 1번지", "삼교리동치미막국수 남항진", "남항진 어촌식당"),
        listOf("아뜨9", "하니엘", "애너벨리"),
        listOf("솔바람다리", "남항진해변")),
    FinishHub("H", "옥계·금진·헌화로", 37.6512, 129.0355, "3km", 3000,
        listOf("진가락", "동해횟집", "삼화횟"),
        listOf("썬옥 베이커리카페", "알로하카페", "크림브리즈"),
        listOf("헌화로", "옥계해변")),
    FinishHub("I", "정동진·심곡", 37.6906, 129.0343, "1.5km", 1500,
        listOf("정동진초당순두부", "할머니초당순두부", "어국"),
        listOf("썬까페", "Cafe PROUST", "수에르떼"),
        listOf("모래시계공원·시간박물관", "정동진해변")),
    FinishHub("J", "안인진·강동", 37.7220, 129.0130, "4km ~ 4.5km", 4500,
        listOf("고향횟집", "안인초가집", "일출봉횟집"),
        listOf("스테이인터뷰 강릉", "어게인디엠카페", "하슬라카페"),
        listOf("안인해변", "강릉통일공원")),
    FinishHub("K", "올림픽파크·교동", 37.7524, 128.8781, "2km", 2000,
        listOf("장군시오야끼 포남직영점", "이모네 생선찜", "동해바다샵1971", "엄지네 포장마차 본점"),
        listOf("카페 이진리", "솔방울 제과", "강릉 빵다방"),
        listOf("강릉올림픽파크", "강릉올림픽뮤지엄")),
    FinishHub("L", "구정·솔향수목원", 37.7280, 128.8642, "1.5km (관광 일부 3.5km)", 3500,
        listOf("숲속한우", "푸른달하우스", "구정막국수 강릉점"),
        listOf("테루아르", "구정미술관카페", "디코이"),
        listOf("강릉솔향수목원", "강릉 굴산사지")),
    FinishHub("M", "성산·구산", 37.7280, 128.8060, "2km", 2000,
        listOf("성왕돈까스", "성산옛집", "옛카나리아"),
        listOf("화조월석", "엘방코", "강릉성산커피"),
        listOf("구산서낭당", "남대천 구산안길")),
    FinishHub("N", "순포·사천진·하평", 37.8280, 128.8580, "2km ~ 2.5km", 2500,
        listOf("황토물회", "항구식당", "제주해인물"),
        listOf("테라로사 사천해변점", "원강희과자점", "포이푸 카페", "쉘리스"),
        listOf("순포습지·순포해변", "사근진해중공원", "사천진해변")),
    FinishHub("O", "모래내·대관령아기동물농장", 37.7060, 128.7480, "4km (부족하면 5km)", 5000,
        listOf("초당막국수", "사천면옥", "솔밭추어탕"),
        listOf("뷰바바", "일포스티노", "알마즈"),
        listOf("모래내한과마을", "대관령아기동물농장", "뒷뜨루관광농원")),
    FinishHub("P", "연곡해변·솔향기캠핑장", 37.8720, 128.8390, "2km", 2000,
        listOf("본가동해막국수", "홍가네국밥", "영진보리밥쌈밥"),
        listOf("카르페디엠커피", "크림하우스", "낮은음자리"),
        listOf("연곡해변", "솔향기캠핑장")),
    FinishHub("Q", "주문진해변·향호", 37.9058, 128.8272, "3km (일부 4km)", 4000,
        listOf("남경막국수", "주문진막국수", "철뚝소머리국밥"),
        listOf("강냉이소쿠리", "순두부젤라또 3호점", "오드커피"),
        listOf("주문진해변", "향호")),
    FinishHub("R", "왕산·대기리", 37.6750, 128.7960, "5km ~ 8km", 8000,
        listOf("대기리벌말식당", "향미식당", "성화천식당"),
        listOf("커피커퍼 왕산점(강릉커피박물관)", "운유쉼터", "마"),
        listOf("안반데기", "노추산 모정탑길"))
)

fun findHub(id: String?): FinishHub? = finishHubs.find { it.id == id }

// ────────────────────────────────────────────────
// 코스별 시작/종료 핀 + Finish Hub 매핑
// (노션 "코스별 시작/종료 핀" 표를 그대로 옮긴 원본 데이터, 57개 전체)
//
// 지금은 핀 "이름"만 있고 좌표는 없는 상태예요. startPinName/finishPinName을
// Kakao 지오코딩(주소/키워드 검색)으로 좌표 변환해야 지도에 실제 마커를 찍을 수 있어요.
// 다만 Finish Hub 추천 로직 자체는 finishHubIds만 있으면 좌표 없이도 바로 동작해요 —
// 즉 "종료 핀의 정확한 좌표"는 지도 표시용이지, 추천 기능의 필수 조건은 아니에요.
// ────────────────────────────────────────────────
data class CourseRoutePin(
    val no: Int,
    val courseName: String,
    val startPinName: String,
    val finishPinName: String,
    val radiusLabel: String,
    val finishHubIds: List<String>   // 두 Hub 경계에 걸치는 코스는 복수 (예: "A","C")
)

val courseRoutePins: List<CourseRoutePin> = listOf(
    CourseRoutePin(1, "경포호 기본런", "허균·허난설헌기념공원", "허균·허난설헌기념공원", "1km", listOf("A")),
    CourseRoutePin(2, "안목해변 커피거리 왕복런", "안목해변 커피거리", "안목해변 커피거리", "800m", listOf("B")),
    CourseRoutePin(3, "강문해변 짧은 해송런", "강문해변", "강문해변", "1km", listOf("A")),
    CourseRoutePin(4, "주문진 해변 → 소돌아들바위공원", "주문진해변", "소돌아들바위공원", "1.5km", listOf("D")),
    CourseRoutePin(5, "순포 → 순긋 → 경포 → 강문", "순포해변", "강문해변", "2km", listOf("A")),
    CourseRoutePin(6, "송정 → 강문해변 해송숲길", "송정해변", "강문해변", "1.5km", listOf("A")),
    CourseRoutePin(7, "경포 → 강문 → 안목", "경포해변", "안목해변 커피거리", "1.5km", listOf("B")),
    CourseRoutePin(8, "사천 → 순포 → 경포 → 강문 → 송정", "사천해변", "송정해변 남단·안목 진입부", "1.5km", listOf("B")),
    CourseRoutePin(9, "경포호 5K", "허균·허난설헌기념공원", "허균·허난설헌기념공원", "1km", listOf("A")),
    CourseRoutePin(10, "경포호 10K", "허균·허난설헌기념공원", "허균·허난설헌기념공원", "1km", listOf("A")),
    CourseRoutePin(11, "경포호 12K - 고래런", "허균·허난설헌기념공원", "허균·허난설헌기념공원", "1km", listOf("A")),
    CourseRoutePin(12, "경포호 붕어빵런", "허균·허난설헌기념공원", "허균·허난설헌기념공원", "1km", listOf("A")),
    CourseRoutePin(13, "남대천 → 안목 5K", "월화교 / 남대천 둔치", "안목해변 커피거리", "800m", listOf("B")),
    CourseRoutePin(14, "강릉역 → 월화거리 → 중앙시장 → 남대천", "강릉역", "월화교", "800m", listOf("F")),
    CourseRoutePin(15, "월화거리 → 중앙시장 → 남대천", "월화거리 강릉역 방향 입구", "월화교", "800m", listOf("F")),
    CourseRoutePin(16, "오죽헌 → 선교장 → 경포호", "오죽헌", "경포호 남쪽·초당 진입부", "1.5km", listOf("A", "C")),
    CourseRoutePin(17, "허균 허난설현 → 초당 → 강문", "허균·허난설헌기념공원", "강문해변", "1km", listOf("A")),
    CourseRoutePin(18, "경포생태저류지 → 메타세쿼이아 → 가시연습지", "경포생태저류지", "가시연습지", "2km", listOf("A", "C")),
    CourseRoutePin(19, "옥계 헌화로 11K", "옥계해변 주차장", "옥계해변 주차장", "3km", listOf("H")),
    CourseRoutePin(20, "사천진리 → 남항진 바우길 5구간", "사천진항 또는 사천진해변", "솔바람다리 남항진측", "1km", listOf("G")),
    CourseRoutePin(21, "경포해변 → 경포호 연결 8~9K", "경포해변", "경포호 남쪽·초당 진입부", "1 ~ 1.5km", listOf("A")),
    CourseRoutePin(22, "주문진항 → 소돌항 → 아들바위공원", "주문진항", "소돌아들바위공원", "1.5km", listOf("D")),
    CourseRoutePin(23, "연곡해변 → 영진해변", "연곡해변", "영진해변", "2km", listOf("E")),
    CourseRoutePin(24, "정동진역 → 모래시계공원 → 정동진해변", "정동진역", "모래시계공원", "1.5km", listOf("I")),
    CourseRoutePin(25, "정동진 코끼리런", "모래시계공원", "모래시계공원", "1.5km", listOf("I")),
    CourseRoutePin(26, "통일공원 → 안인진", "강릉통일공원", "안인진항", "2 ~ 4km", listOf("J")),
    CourseRoutePin(27, "강릉올림픽파크 순환런", "강릉올림픽파크", "강릉올림픽파크", "2km", listOf("K")),
    CourseRoutePin(28, "강릉솔향수목원 워크", "솔향수목원 제3주차장/입구", "솔향수목원 제3주차장 / 입구", "1.5km", listOf("L")),
    CourseRoutePin(29, "성산 구산안길 남대천 리버런", "성산면사무소", "성산 먹거리촌 입구", "2km", listOf("M")),
    CourseRoutePin(30, "구산서낭당 남대천 문화런", "성산면사무소", "성산 먹거리촌 입구", "2km", listOf("M")),
    CourseRoutePin(31, "성산 먹거리촌 회복런", "성산 먹거리촌", "성산 먹거리촌", "1.5 ~ 2km", listOf("M")),
    CourseRoutePin(32, "순포습지 생태 루프런", "순포습지 입구", "순포습지 입구", "2.5km", listOf("N")),
    CourseRoutePin(33, "순포해변 → 사근진해중공원 포토런", "순포해변·순포습지", "사근진해중공원 전망대", "2 ~ 2.5km", listOf("N")),
    CourseRoutePin(34, "모래내 한과마을 먹거리런", "모래내한과마을·한과체험전시관", "모래내한과마을·한과체험전시관", "4km", listOf("O")),
    CourseRoutePin(35, "대관령아기동물농장 패밀리 관광런", "대관령아기동물농장 입구", "대관령아기동물농장 입구", "4 ~ 5km", listOf("O")),
    CourseRoutePin(36, "하평해변 해다리바위 미니런", "하평해변", "하평해변·해다리바위 인근", "2 ~ 2.5km", listOf("N")),
    CourseRoutePin(37, "연곡해변 솔향기 해송런", "연곡해변 주차장", "솔향기캠핑장 입구·연곡해변", "2km", listOf("P")),
    CourseRoutePin(38, "연곡해변 → 영진해변 바다", "연곡해변", "영진해변", "1.5km", listOf("E")),
    CourseRoutePin(39, "영진해변 → 영진항 포토런", "영진해변", "영진항", "1.5km", listOf("E")),
    CourseRoutePin(40, "연곡해변 → 주문진항 먹거리런", "연곡해변", "주문진항·수산시장 입구", "1km", listOf("D")),
    CourseRoutePin(41, "연곡해변 → 영진해변 → 영진항 → 주문진항", "연곡해변", "주문진해변", "3km", listOf("Q")),
    CourseRoutePin(42, "연곡솔향기캠핑장 회복", "솔향기캠핑장 입구", "솔향기캠핑장 입구", "2km", listOf("P")),
    CourseRoutePin(43, "주문진해변 → 향호 회복런", "주문진해변 주차장", "주문진해변 주차장", "3km", listOf("Q")),
    CourseRoutePin(44, "주문진항 → 수산시장 먹거리런", "주문진항 공영주차장", "주문진수산시장 입구", "800m ~ 1km", listOf("D")),
    CourseRoutePin(45, "주문진항 → 주문진등대 → 소돌항구뷰런", "주문진항", "소돌아들바위공원", "1.5km", listOf("D")),
    CourseRoutePin(46, "주문진해변 → 주문진항 해안 10K", "주문진해변", "주문진항·수산시장", "1km", listOf("D")),
    CourseRoutePin(47, "주문진해변 → 향호 바람의 길 장거리런", "주문진해변 주차장", "주문진해변 주차장", "3km", listOf("Q")),
    CourseRoutePin(48, "안인항 → 안인해변 동해안 클래식 5K", "안인항", "안인항", "2 ~ 4km", listOf("J")),
    CourseRoutePin(49, "정동진 해안런 8K", "정동진역", "정동진역", "1.5 ~ 2km", listOf("I")),
    CourseRoutePin(50, "옥계 오션런 10K", "옥계해변 주차장", "옥계해변 주차장", "3km", listOf("H")),
    CourseRoutePin(51, "구정 평야런 12K", "구정면사무소", "구정면사무소", "3 ~ 4km", listOf("L")),
    CourseRoutePin(52, "왕산 계곡런 15K", "왕산면사무소", "왕산면사무소", "5km", listOf("R")),
    CourseRoutePin(53, "강동 → 정동진 LSD 18K", "안인항", "안인항", "2 ~ 4km", listOf("J")),
    CourseRoutePin(54, "안인 → 정동진 → 심곡 → 금진 → 옥계 해안 종단 24K", "안인항", "옥계해변 주차장", "3km", listOf("H")),
    CourseRoutePin(55, "왕산 → 구정 크로스 21K", "왕산면사무소", "왕산면사무소", "5km", listOf("R")),
    CourseRoutePin(56, "강릉 남부 30K 챌린지", "안인항", "옥계해변 주차장", "3km", listOf("H")),
    CourseRoutePin(57, "남부권 시그니처 풀코스 42.2K", "안인항", "안인항", "2 ~ 4km", listOf("J"))
)

fun findRoutePin(courseName: String): CourseRoutePin? = courseRoutePins.find { it.courseName == courseName }
