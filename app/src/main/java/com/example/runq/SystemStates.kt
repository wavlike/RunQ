package com.example.runq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ════════════════════════════════════════════════════════
// Figma "50~54 System / *": 권한 요청 / 빈 상태 / 에러 / 로딩 / 스켈레톤.
// 특정 화면 전용이 아니라 여러 화면이 공유하는 작은 상태 컴포넌트로 만들어서,
// 실제로 그 상황이 발생하는 지점(Place 탭, TourAPI 호출 등)에 그대로 꽂아 쓴다.
// ════════════════════════════════════════════════════════

// 50/50-2 System / Permission — 위치/알림 등 권한이 필요할 때 공통으로 쓰는 안내 카드.
@Composable
fun PermissionPromptView(
    icon: String,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(20.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(RunLime.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 24.sp) }
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

// 51 System / Empty — 검색·목록이 비어있을 때.
@Composable
fun EmptyStateView(icon: String, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 34.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = RunGray, textAlign = TextAlign.Center)
    }
}

// 52 System / Error — 네트워크·GPS 등 호출이 실패했을 때, 재시도 버튼 포함.
@Composable
fun ErrorStateView(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠", fontSize = 30.sp, color = Color(0xFFB95D5D))
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = RunGray, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(16.dp)) {
                Text("다시 시도", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
