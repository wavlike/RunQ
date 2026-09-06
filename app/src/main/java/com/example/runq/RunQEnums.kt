package com.example.runq

// ════════════════════════════════════════════════════════
// RunQ DB 엑셀 "99_LISTS" 시트 기준 enum.
// 알 수 없거나 아직 비어있는 값은 UNKNOWN으로 떨어뜨려서 파싱이 절대 죽지 않게 한다.
// ════════════════════════════════════════════════════════

enum class ContentStatus { ACTIVE, HIDDEN, DRAFT, UNKNOWN }

enum class Terrain(val label: String) {
    FLAT("평지"), ROLLING("완만한 굴곡"), HILL("언덕"), MIXED("혼합"),
    TRAIL("트레일"), COAST("해안"), OTHER("기타"), UNKNOWN("-")
}

enum class Difficulty(val label: String) {
    EASY("쉬움"), NORMAL("보통"), HARD("어려움"), UNKNOWN("-")
}

enum class TrafficLevel(val label: String) {
    LOW("여유"), MEDIUM("보통"), HIGH("혼잡"), UNKNOWN("-")
}

fun parseContentStatus(raw: String?): ContentStatus =
    ContentStatus.entries.find { it.name == raw?.trim()?.uppercase() } ?: ContentStatus.DRAFT

fun parseTerrain(raw: String?): Terrain =
    Terrain.entries.find { it.name == raw?.trim()?.uppercase() } ?: Terrain.UNKNOWN

fun parseDifficulty(raw: String?): Difficulty =
    Difficulty.entries.find { it.name == raw?.trim()?.uppercase() } ?: Difficulty.UNKNOWN

fun parseTrafficLevel(raw: String?): TrafficLevel =
    TrafficLevel.entries.find { it.name == raw?.trim()?.uppercase() } ?: TrafficLevel.UNKNOWN
