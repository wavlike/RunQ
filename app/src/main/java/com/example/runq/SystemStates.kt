package com.example.runq

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ════════════════════════════════════════════════════════
// Figma "50~54 System / *": 권한 요청 / 빈 상태 / 에러 / 로딩 / 스켈레톤.
// 특정 화면 전용이 아니라 여러 화면이 공유하는 작은 상태 컴포넌트로 만들어서,
// 실제로 그 상황이 발생하는 지점(Place 탭, TourAPI 호출 등)에 그대로 꽂아 쓴다.
//
// mascot(R.drawable.mascot_dragon/mascot_bear)이 주어지면 이모지 대신 그 마스코트 이미지를
// 보여준다 — 위치/GPS/검색처럼 "찾는" 상황엔 도롱뇽 마스코트를, 알림/기록/저장/네트워크처럼
// "쌓이는·연결되는" 상황엔 곰 마스코트를 쓰는 것으로 맞춰뒀다(Figma 구성 그대로).
// ════════════════════════════════════════════════════════

@Composable
private fun StateGlyph(icon: String?, mascot: Int?, sizeDp: Int, circleBg: Boolean) {
    if (mascot != null) {
        Image(
            painter = painterResource(mascot),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(sizeDp.dp)
        )
    } else if (icon != null) {
        if (circleBg) {
            Box(
                modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(RunLime.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) { Text(icon, fontSize = (sizeDp * 0.42f).sp) }
        } else {
            Text(icon, fontSize = (sizeDp * 0.85f).sp)
        }
    }
}

// 50/50-2 System / Permission — 위치/알림 등 권한이 필요할 때 화면 안에 끼워 쓰는 안내 카드.
@Composable
fun PermissionPromptView(
    icon: String,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    mascot: Int? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(20.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StateGlyph(icon = icon, mascot = mascot, sizeDp = if (mascot != null) 72 else 56, circleBg = mascot == null)
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunBlack, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = RunGray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAction,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
        ) { Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

// 50/50-2 System / Permission — 전체화면 버전. 시스템 권한 다이얼로그를 띄우기 전에
// "왜 필요한지" 먼저 설명하는 화면(위치 권한 안내 / 알림 권한 안내)에 쓴다.
@Composable
fun PermissionRationaleScreen(
    mascot: Int,
    headerTitle: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(RunCream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 40.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(RunWhite)
                    .border(1.dp, RunBorderGray, CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = RunBlack, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(headerTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(mascot), contentDescription = null,
                contentScale = ContentScale.Fit, modifier = Modifier.size(180.dp)
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "RunQ를 제대로 즐기려면\n권한이 필요해요.",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunBlack, lineHeight = 29.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(description, fontSize = 13.sp, color = RunGray, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
            ) { Text(actionLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(14.dp))
            Text(
                "나중에 하기", fontSize = 13.sp, color = RunGray, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { onSkip() }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "언제든 설정에서 권한을 변경할 수 있어요.", fontSize = 11.sp, color = RunGray,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
}

// 50-3/52-2 System / GPS Error — 권한은 있는데 위치 신호를 못 잡을 때 뜨는 확인 모달.
@Composable
fun GpsErrorModal(onCancel: () -> Unit, onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(RunBlack.copy(alpha = 0.45f)).clickable(enabled = false) {})
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp)
            .clip(RoundedCornerShape(24.dp)).background(RunWhite).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.mascot_dragon), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text("GPS 위치를 확인할 수 없어요.", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RunBlack, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "위치 서비스가 꺼져 있거나 신호가 약해요.\n러닝을 시작하려면 위치 설정을 확인해주세요.",
            fontSize = 12.sp, color = RunGray, lineHeight = 18.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp))
                    .background(RunBgGray).clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) { Text("취소", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RunBlack) }
            Box(
                modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp))
                    .background(RunLime).clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) { Text("설정으로 이동", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RunBlack) }
        }
    }
}

// 51 System / Empty — 검색·목록이 비어있을 때. mascot/actionLabel은 선택 사항이라 기존 호출부는
// 그대로 두고, 새로 필요한 화면만 마스코트+버튼을 채워 쓸 수 있다.
@Composable
fun EmptyStateView(
    icon: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    mascot: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StateGlyph(icon = icon, mascot = mascot, sizeDp = if (mascot != null) 88 else 34, circleBg = false)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = RunGray, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
            ) { Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// 52 System / Error — 네트워크·GPS 등 호출이 실패했을 때, 재시도(또는 설정 이동) 버튼 포함.
@Composable
fun ErrorStateView(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    mascot: Int? = null,
    retryLabel: String = "다시 시도"
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (mascot != null) {
            StateGlyph(icon = null, mascot = mascot, sizeDp = 88, circleBg = false)
        } else {
            Text("⚠", fontSize = 30.sp, color = Color(0xFFB95D5D))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = RunGray, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(16.dp)) {
                Text(retryLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 53 System / Loading — 짧은 API 호출용 스피너.
@Composable
fun LoadingView(message: String = "불러오는 중이에요…", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = RunPurple, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text(message, fontSize = 12.sp, color = RunGray)
    }
}

// 54 System / Skeleton — 목록이 로딩 중일 때 카드 자리만 은은하게 반짝이며 보여준다.
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier, heightDp: Int = 96) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(18.dp))
            .background(RunBgGray.copy(alpha = alpha))
    )
}

@Composable
fun SkeletonList(count: Int = 3, itemHeightDp: Int = 96, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { SkeletonListItem(heightDp = itemHeightDp) }
    }
}
