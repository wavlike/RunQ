package com.example.runq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ════════════════════════════════════════════════════════
// My 탭: Figma "40~46 My/*" 기준. 실제 서버 계정이 없어서 로그인/러닝 기록/저장 목록/
// 프로필은 전부 기기 로컬 저장(LocalStore.kt)으로 동작한다 — 숫자와 목록은 전부 실제 값.
// ════════════════════════════════════════════════════════

sealed class MyStep {
    object Overview : MyStep()
    object History : MyStep()
    data class RunDetail(val recordId: String) : MyStep()
    object Saved : MyStep()
    object Settings : MyStep()
    object EditProfile : MyStep()
    object DeleteAccount : MyStep()
}

@Composable
fun MyFlow(onLogout: () -> Unit) {
    var step by remember { mutableStateOf<MyStep>(MyStep.Overview) }
    when (val s = step) {
        is MyStep.Overview -> MyOverviewScreen(
            onOpenHistory = { step = MyStep.History },
            onOpenSaved = { step = MyStep.Saved },
            onOpenSettings = { step = MyStep.Settings },
            onEditProfile = { step = MyStep.EditProfile }
        )
        is MyStep.History -> MyHistoryScreen(
            onBack = { step = MyStep.Overview },
            onOpenRecord = { id -> step = MyStep.RunDetail(id) }
        )
        is MyStep.RunDetail -> MyRunDetailScreen(recordId = s.recordId, onBack = { step = MyStep.History })
        is MyStep.Saved -> MySavedScreen(onBack = { step = MyStep.Overview })
        is MyStep.Settings -> MySettingsScreen(
            onBack = { step = MyStep.Overview },
            onDeleteAccount = { step = MyStep.DeleteAccount },
            onLogout = onLogout
        )
        is MyStep.EditProfile -> MyEditProfileScreen(onBack = { step = MyStep.Overview })
        is MyStep.DeleteAccount -> MyDeleteAccountScreen(
            onCancel = { step = MyStep.Settings },
            onConfirmDelete = { clearAllLocalData(); onLogout() }
        )
    }
}

@Composable
private fun MyBackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(RunWhite)
                .border(1.dp, RunBorderGray, CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = RunBlack, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunBlack)
    }
}

// ── 40 My / Overview ─────────────────────────────────
@Composable
fun MyOverviewScreen(onOpenHistory: () -> Unit, onOpenSaved: () -> Unit, onOpenSettings: () -> Unit, onEditProfile: () -> Unit) {
    val totalCount = remember { RunHistoryStore.totalCount() }
    val totalKm = remember { RunHistoryStore.totalKm() }
    val thisMonth = remember { RunHistoryStore.thisMonthCount() }
    val nickname = remember { ProfileStore.nickname }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("MY", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(4.dp))
        Text("나의 러닝과 저장 목록을 한눈에", fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onEditProfile() }) {
            Box(
                modifier = Modifier.size(58.dp).clip(CircleShape).background(RunLime.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Person, contentDescription = null, tint = RunBlack, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("$nickname 님", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Text("프로필 수정 ›", fontSize = 12.sp, color = RunGray)
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(20.dp)).padding(vertical = 20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewStat("${totalCount}회", "누적 러닝", Modifier.weight(1f))
                OverviewStat(String.format("%.1fkm", totalKm), "총 거리", Modifier.weight(1f))
                OverviewStat("${thisMonth}회", "이번 달 러닝", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("내 활동", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(20.dp))
        ) {
            Column {
                MyMenuRow("↗", "내 러닝 기록", null, onOpenHistory)
                MyMenuDivider()
                MyMenuRow("♡", "저장한 코스 및 장소", null, onOpenSaved)
                MyMenuDivider()
                MyMenuRow("⚙", "앱 설정", "프로필 · 알림 · 계정", onOpenSettings)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OverviewStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = RunGray)
    }
}

@Composable
private fun MyMenuRow(glyph: String, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(RunBgGray), contentAlignment = Alignment.Center) {
            Text(glyph, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = RunGray)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = RunGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MyMenuDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(RunBorderGray))
    }
}

// ── 41 My / History ──────────────────────────────────
@Composable
fun MyHistoryScreen(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val records = remember { RunHistoryStore.all() }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        MyBackHeader("내 러닝 기록", onBack)
        Spacer(Modifier.height(12.dp))
        Text("최근 러닝 · 총 ${records.size}회", fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))
        if (records.isEmpty()) {
            EmptyStateView(
                icon = "🏃",
                title = "아직 러닝 기록이 없어요",
                message = "코스를 골라 첫 러닝을 완주하면 여기에 기록이 쌓여요.",
                modifier = Modifier.padding(top = 40.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records) { record -> HistoryCard(record) { onOpenRecord(record.id) } }
            }
        }
    }
}

@Composable
private fun HistoryCard(record: RunRecord, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp))
            .clickable { onClick() }.padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RunRouteThumbnail(Modifier.size(84.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.courseName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(4.dp))
                Text("${record.dateLabel()} · ${record.timeOfDayLabel()}", fontSize = 11.sp, color = RunGray)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${String.format("%.2f", record.distanceKm)} KM  ·  ${record.durationLabel()}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunBlack
                )
                Spacer(Modifier.height(4.dp))
                Text("평균 ${record.paceLabel()}/KM", fontSize = 10.sp, color = RunGray)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = RunGray, modifier = Modifier.size(18.dp))
        }
    }
}

// 실측 GPS 경로 대신 쓰는 장식용 루프 썸네일(코스 리스트 카드와 같은 모티프)
@Composable
private fun RunRouteThumbnail(modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8EDE5)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(52.dp, 40.dp)) {
            drawOval(
                color = RunBlack.copy(alpha = 0.5f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }
}

// ── 42 My / Run Detail ───────────────────────────────
@Composable
fun MyRunDetailScreen(recordId: String, onBack: () -> Unit) {
    val record = remember { RunHistoryStore.get(recordId) }
    val course = remember { record?.courseId?.let { id -> RunQData.courses.find { it.id == id } } }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        MyBackHeader("러닝 기록 상세", onBack)
        if (record == null) {
            Spacer(Modifier.height(20.dp))
            Text("기록을 찾을 수 없어요.", color = RunGray)
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        Text("${record.dateLabel()} · ${record.courseName}", fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))
        if (course != null) {
            CourseMapCard(course = course, heightDp = 230)
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEDEEE9)),
                contentAlignment = Alignment.Center
            ) { RunRouteThumbnail(Modifier.size(140.dp)) }
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            CourseInfoLine("거리", String.format("%.2f km", record.distanceKm), Modifier.weight(1f))
            CourseInfoLine("시간", record.durationLabel(), Modifier.weight(1f))
            CourseInfoLine("평균 페이스", record.paceLabel(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(16.dp)).padding(16.dp)
        ) {
            Column {
                Text("날씨", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(record.tempLabel, record.pm10Label).joinToString(" · ").ifBlank { "당시 날씨 정보가 저장되지 않았어요." },
                    fontSize = 11.sp, color = RunGray
                )
            }
        }
        if (course != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(16.dp)).padding(16.dp)
            ) {
                Column {
                    Text("코스 다시 보기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Spacer(Modifier.height(6.dp))
                    Text(course.name, fontSize = 11.sp, color = RunGray)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ── 43 My / Saved ────────────────────────────────────
@Composable
fun MySavedScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) } // 0 = 코스, 1 = 장소
    val savedCourses = remember { SavedItemsStore.savedCourses() }
    val savedPlaces = remember { SavedItemsStore.savedPlaces() }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        MyBackHeader("저장한 코스 · 장소", onBack)
        Spacer(Modifier.height(10.dp))
        Text("나중에 달릴 코스와 들르고 싶은 장소를 모아두었어요.", fontSize = 12.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(22.dp)).padding(4.dp)
        ) {
            SavedTabChip("코스 ${savedCourses.size}", tab == 0, Modifier.weight(1f)) { tab = 0 }
            SavedTabChip("EAT · CAFE · SEE ${savedPlaces.size}", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }
        Spacer(Modifier.height(18.dp))
        if (tab == 0) {
            if (savedCourses.isEmpty()) {
                EmptyStateView("📌", "저장한 코스가 없어요", "코스 상세에서 \"코스 저장하기\"를 눌러보세요.", Modifier.padding(top = 30.dp))
            } else {
                Text("다음 러닝 후보를 모아두었어요.", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RunBlack)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(savedCourses) { course -> SavedCourseCard(course) }
                }
            }
        } else {
            if (savedPlaces.isEmpty()) {
                EmptyStateView("📍", "저장한 장소가 없어요", "Place 탭에서 하트를 눌러 장소를 저장해보세요.", Modifier.padding(top = 30.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(savedPlaces) { place -> SavedPlaceCard(place) }
                }
            }
        }
    }
}

@Composable
private fun SavedTabChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Color(0xFFF6F7BA) else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) RunBlack else RunGray)
    }
}

@Composable
private fun SavedCourseCard(course: Course) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(20.dp)).padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RunRouteThumbnail(Modifier.size(88.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(course.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Spacer(Modifier.height(4.dp))
                    Text("${course.distanceLabel()} · ${course.timeLabel()} · ${course.terrain.label}", fontSize = 11.sp, color = RunGray)
                }
                Text(
                    "♥", fontSize = 15.sp, color = RunPurple,
                    modifier = Modifier.clip(CircleShape).clickable { SavedItemsStore.toggleCourse(course.id) }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "다음 러닝으로 설정 →", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = RunBlack,
                modifier = Modifier.clickable { SavedItemsStore.nextCourseId = course.id }
            )
        }
    }
}

@Composable
private fun SavedPlaceCard(place: FinishHubPlace) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(place.category.accent.copy(alpha = 0.35f)))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(place.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                Spacer(Modifier.height(4.dp))
                Text(place.category.label, fontSize = 11.sp, color = RunGray)
            }
            val placeId = place.id
            if (placeId != null) {
                Text(
                    "♥", fontSize = 15.sp, color = RunPurple,
                    modifier = Modifier.clip(CircleShape).clickable { SavedItemsStore.togglePlace(placeId) }.padding(4.dp)
                )
            }
        }
    }
}

// ── 44 My / Settings ─────────────────────────────────
@Composable
fun MySettingsScreen(onBack: () -> Unit, onDeleteAccount: () -> Unit, onLogout: () -> Unit) {
    var pushEnabled by remember { mutableStateOf(ProfileStore.pushEnabled) }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        MyBackHeader("설정", onBack)
        Spacer(Modifier.height(8.dp))
        Text("RunQ 이용 환경을 관리할 수 있어요.", fontSize = 12.sp, color = RunGray)
        Spacer(Modifier.height(20.dp))

        Text("앱 설정", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp))
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("푸시 알림", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                        Text("러닝 · 추천 알림 받기", fontSize = 11.sp, color = RunGray)
                    }
                    Switch(
                        checked = pushEnabled,
                        onCheckedChange = { pushEnabled = it; ProfileStore.pushEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = RunLime, checkedThumbColor = RunBlack)
                    )
                }
                MyMenuDivider()
                SettingsLinkRow("위치 정보 권한 설정", "러닝 기록 및 현재 위치 사용") { /* 시스템 앱 설정으로 이동은 다음 단계 */ }
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("계정", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp))
        ) {
            Column {
                SettingsLinkRow("비밀번호 변경", null) { /* 실제 계정 시스템 붙기 전까지는 자리표시 */ }
                MyMenuDivider()
                SettingsLinkRow("로그아웃", null, onClick = onLogout)
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("계정 관리", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onDeleteAccount() }.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("회원탈퇴", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFBE4444))
                Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFBE4444), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("RUNQ · VERSION 1.0.0", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunGray)
        Spacer(Modifier.height(4.dp))
        Text("최신 버전을 사용 중이에요.", fontSize = 11.sp, color = RunGray)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsLinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = RunGray)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = RunGray, modifier = Modifier.size(20.dp))
    }
}

// ── 45 My / Edit Profile ─────────────────────────────
@Composable
fun MyEditProfileScreen(onBack: () -> Unit) {
    var nickname by remember { mutableStateOf(ProfileStore.nickname) }

    Column(modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(RunWhite)
                    .border(1.dp, RunBorderGray, CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = RunBlack, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Text("프로필 수정", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RunBlack, modifier = Modifier.weight(1f))
            Text(
                "완료", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RunPurple,
                modifier = Modifier.clickable { ProfileStore.nickname = nickname.ifBlank { "러너" }; onBack() }
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunPurple)
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(RunLime.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = RunBlack, modifier = Modifier.size(38.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "프로필 사진 변경", fontSize = 13.sp, color = RunGray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Text("닉네임", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = nickname, onValueChange = { if (it.length <= 10) nickname = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = RunBlack, fontFamily = com.example.runq.ui.theme.NotoSansKR),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(RunBlack)
                    )
                }
                if (nickname.isNotEmpty()) {
                    Text(
                        "×", fontSize = 15.sp, color = RunGray,
                        modifier = Modifier.clip(CircleShape).background(Color(0xFFF0EFEA)).clickable { nickname = "" }.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("2~10자 이내의 한글, 영문, 숫자만 사용할 수 있어요.", fontSize = 11.sp, color = RunGray)
        Spacer(Modifier.height(24.dp))
        Text("계정 이메일", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF1F0EB)).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) { Text("runq@email.com", fontSize = 14.sp, color = RunGray) }
        Spacer(Modifier.height(8.dp))
        Text("변경할 수 없는 정보예요.", fontSize = 11.sp, color = RunGray)
        Spacer(Modifier.height(28.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF3ECDD)).padding(16.dp)) {
            Column {
                Text("RUNQ PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunGray)
                Spacer(Modifier.height(6.dp))
                Text("프로필 정보는 러닝 기록과 저장 목록에 함께 사용돼요.", fontSize = 12.sp, color = RunGray)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ── 46 My / Delete Account Confirm ───────────────────
@Composable
fun MyDeleteAccountScreen(onCancel: () -> Unit, onConfirmDelete: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 뒤에 살짝 보이는 설정 화면(딤 처리) — 실제 뒤로가기 스택 대신 정적 배경으로 표현
        Box(modifier = Modifier.fillMaxSize().background(RunCream))
        Box(modifier = Modifier.fillMaxSize().background(RunBlack.copy(alpha = 0.38f)))

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(RunWhite).padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Box(
                modifier = Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(RunBorderGray).align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(20.dp))
            Text("계정을 삭제하시겠어요?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            Spacer(Modifier.height(10.dp))
            Text(
                "지금까지 기록한 러닝 데이터와 저장한 코스·장소 정보가 모두 삭제되며 복구할 수 없습니다.",
                fontSize = 13.sp, color = RunGray, lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF8ECEA)).padding(16.dp)) {
                Column {
                    Text("삭제되는 정보", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB95D5D))
                    Spacer(Modifier.height(8.dp))
                    Text("· 러닝 기록   · 저장한 코스\n· 저장한 장소   · 프로필 정보", fontSize = 12.sp, color = RunBlack, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(18.dp))
                        .background(RunBgGray).clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) { Text("취소", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack) }
                Box(
                    modifier = Modifier.weight(1.2f).height(54.dp).clip(RoundedCornerShape(18.dp))
                        .background(RunWhite).border(1.dp, Color(0xFFB95D5D), RoundedCornerShape(18.dp))
                        .clickable { onConfirmDelete() },
                    contentAlignment = Alignment.Center
                ) { Text("탈퇴하기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB95D5D)) }
            }
        }
    }
}
