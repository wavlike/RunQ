package com.example.runq

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 완료된 러닝 한 건의 기록
data class RunRecord(
    val dateLabel: String,
    val distanceKm: Double,
    val durationSeconds: Int
)

// MainWithTabs에서 한 번만 생성해 RunningScreen/HistoryScreen에 함께 전달합니다.
// 탭을 왔다갔다 해도(예: 러닝 중 홈 탭을 눌렀다 돌아오는 경우) 진행 중인 러닝 상태가
// 컴포저블 재구성으로 초기화되지 않도록 탭 전환 위쪽에서 상태를 들고 있습니다.
class RunSessionState {
    var isRunning by mutableStateOf(false)
    var distanceKm by mutableStateOf(0.0)
    var elapsedSeconds by mutableStateOf(0)
    val history = mutableStateListOf<RunRecord>()

    fun finishRun() {
        if (distanceKm > 0.0) {
            history.add(0, RunRecord(dateLabel = todayLabel(), distanceKm = distanceKm, durationSeconds = elapsedSeconds))
        }
        isRunning = false
        distanceKm = 0.0
        elapsedSeconds = 0
    }
}

private fun todayLabel(): String =
    SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date())

// "mm:ss" 형태로 경과 시간 표시
fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

// 1km당 평균 페이스를 "m'ss\"" 형태로 표시
fun formatPace(totalSeconds: Int, distanceKm: Double): String {
    if (distanceKm <= 0.0) return "0'00\""
    val paceSeconds = (totalSeconds / distanceKm).toInt()
    val m = paceSeconds / 60
    val s = paceSeconds % 60
    return "${m}'${s.toString().padStart(2, '0')}\""
}

// 체중 정보가 없어 1km당 약 60kcal로 어림잡은 대략적인 추정치
fun estimateCalories(distanceKm: Double): Int = (distanceKm * 60).toInt()
