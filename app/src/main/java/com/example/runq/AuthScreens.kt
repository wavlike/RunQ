package com.example.runq

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ════════════════════════════════════════════════════════
// Figma "01~05 Auth": Entry / Sign Up / Log In / Password Recovery / Terms & Privacy.
// 이 앱엔 아직 실제 서버 계정 시스템이 없어서(로그인 성공 시 그냥 Main으로 진입),
// 이 화면들은 폼 UI와 화면 흐름만 실제로 동작하고 인증 자체는 로컬 목업이다.
// (SNS 로그인은 실제 OAuth 연동 없이는 동작을 흉내낼 수 없어 뺐다 — 버튼만 있고 아무 것도
// 안 되는 상태로 두지 않기로 함.)
// ════════════════════════════════════════════════════════

sealed class AuthStep {
    object Entry : AuthStep()
    object SignUp : AuthStep()
    object LogIn : AuthStep()
    object PasswordRecovery : AuthStep()
    data class Terms(val from: AuthStep) : AuthStep()
}

@Composable
fun AuthFlow(onAuthSuccess: () -> Unit) {
    var step by remember { mutableStateOf<AuthStep>(AuthStep.Entry) }
    when (val s = step) {
        is AuthStep.Entry -> AuthEntryScreen(
            onSignUp = { step = AuthStep.SignUp },
            onLogIn = { step = AuthStep.LogIn },
            onBrowse = onAuthSuccess
        )
        is AuthStep.SignUp -> AuthSignUpScreen(
            onBack = { step = AuthStep.Entry },
            onCreateAccount = { onAuthSuccess() },
            onGoToLogin = { step = AuthStep.LogIn },
            onOpenTerms = { step = AuthStep.Terms(s) }
        )
        is AuthStep.LogIn -> AuthLogInScreen(
            onBack = { step = AuthStep.Entry },
            onLogin = { onAuthSuccess() },
            onForgotPassword = { step = AuthStep.PasswordRecovery },
            onGoToSignUp = { step = AuthStep.SignUp }
        )
        is AuthStep.PasswordRecovery -> AuthPasswordRecoveryScreen(onBack = { step = AuthStep.LogIn })
        is AuthStep.Terms -> AuthTermsScreen(onBack = { step = s.from })
    }
}

@Composable
private fun AmbientGlows(lime: Pair<Int, Int>, lavender: Pair<Int, Int>, limeSize: Int = 300, lavenderSize: Int = 300) {
    Box(
        modifier = Modifier.size(limeSize.dp).offset(x = lime.first.dp, y = lime.second.dp).blur(70.dp)
            .background(Brush.radialGradient(listOf(RunLime.copy(alpha = 0.55f), Color.Transparent)), CircleShape)
    )
    Box(
        modifier = Modifier.size(lavenderSize.dp).offset(x = lavender.first.dp, y = lavender.second.dp).blur(70.dp)
            .background(Brush.radialGradient(listOf(RunLavender.copy(alpha = 0.55f), Color.Transparent)), CircleShape)
    )
}

// ── 01 Auth / Entry ──────────────────────────────────
@Composable
fun AuthEntryScreen(onSignUp: () -> Unit, onLogIn: () -> Unit, onBrowse: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(RunBlack)) {
        AmbientGlows(lime = -115 to -90, lavender = 185 to 225, limeSize = 310, lavenderSize = 330)

        Column(modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp)) {
            Text("RunQ", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RunWhite, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(50.dp))
            Text(
                "나만의 코스를 발견하고\n지금 바로 달려보세요.",
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RunWhite, lineHeight = 36.sp
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(RunBlack.copy(alpha = 0.6f)).padding(16.dp).padding(bottom = 24.dp, top = 20.dp)
        ) {
            Button(
                onClick = onSignUp,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
            ) { Text("가입하고 시작하기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLogIn,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, RunLavender),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RunWhite)
            ) { Text("로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(14.dp))
            Text(
                "둘러보기", fontSize = 12.sp, color = RunWhite.copy(alpha = 0.68f),
                modifier = Modifier.fillMaxWidth().clickable { onBrowse() }, textAlign = TextAlign.Center
            )
        }
    }
}

// ── 공용 인풋 필드 ──────────────────────────────────
@Composable
private fun AuthField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text
) {
    var visible by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp))
            .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(leadingIcon, contentDescription = null, tint = RunGray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextFieldWithPlaceholder(
                value = value, onValueChange = onValueChange, placeholder = placeholder,
                modifier = Modifier.weight(1f),
                visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType
            )
            if (isPassword) {
                Icon(
                    if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) "비밀번호 숨기기" else "비밀번호 보기",
                    tint = RunGray, modifier = Modifier.size(20.dp).clickable { visible = !visible }
                )
            }
        }
    }
}

@Composable
private fun BasicTextFieldWithPlaceholder(
    value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Box(modifier = modifier) {
        if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = RunGray)
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = RunBlack, fontFamily = com.example.runq.ui.theme.NotoSansKR),
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(RunBlack)
        )
    }
}

@Composable
private fun AuthBackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(RunWhite)
            .border(1.dp, RunBorderGray, CircleShape).clickable { onBack() },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = RunBlack, modifier = Modifier.size(18.dp)) }
}

// ── 02 Auth / Sign Up ────────────────────────────────
@Composable
fun AuthSignUpScreen(onBack: () -> Unit, onCreateAccount: () -> Unit, onGoToLogin: () -> Unit, onOpenTerms: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.length >= 4 && nickname.isNotBlank()

    Box(modifier = Modifier.fillMaxSize().background(RunCream)) {
        AmbientGlows(lime = -135 to -115, lavender = 230 to 15, limeSize = 290, lavenderSize = 280)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(60.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AuthBackButton(onBack) }
            Spacer(Modifier.height(20.dp))
            Text("RunQ 시작하기", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            Spacer(Modifier.height(10.dp))
            Text("계정을 만들고 나만의 러닝 코스와 기록을 저장해보세요.", fontSize = 13.sp, color = RunGray)
            Spacer(Modifier.height(30.dp))
            AuthField(email, { email = it }, "이메일", Icons.Filled.Email, keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            AuthField(password, { password = it }, "비밀번호", Icons.Filled.Lock, isPassword = true)
            Spacer(Modifier.height(12.dp))
            AuthField(nickname, { nickname = it }, "닉네임", Icons.Filled.Person)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    ProfileStore.nickname = nickname
                    onCreateAccount()
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
            ) { Text("가입하기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
            Row {
                Text("이미 계정이 있나요? ", fontSize = 13.sp, color = RunGray)
                Text("로그인", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunPurple, modifier = Modifier.clickable { onGoToLogin() })
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "가입하면 RunQ 이용약관 및 개인정보처리방침에 동의하게 됩니다.",
                fontSize = 11.sp, color = RunGray, modifier = Modifier.clickable { onOpenTerms() }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── 03 Auth / Log In ─────────────────────────────────
@Composable
fun AuthLogInScreen(onBack: () -> Unit, onLogin: () -> Unit, onForgotPassword: () -> Unit, onGoToSignUp: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(RunCream)) {
        AmbientGlows(lime = -145 to -110, lavender = 225 to 20, limeSize = 290, lavenderSize = 285)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(60.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AuthBackButton(onBack) }
            Spacer(Modifier.height(20.dp))
            Text("다시 만났네요!", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = RunBlack)
            Spacer(Modifier.height(10.dp))
            Text("로그인하고 저장한 코스와 러닝 기록을 이어서 확인하세요.", fontSize = 13.sp, color = RunGray)
            Spacer(Modifier.height(30.dp))
            AuthField(email, { email = it }, "이메일", Icons.Filled.Email, keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            AuthField(password, { password = it }, "비밀번호", Icons.Filled.Lock, isPassword = true)
            Spacer(Modifier.height(10.dp))
            Text(
                "비밀번호를 잊으셨나요?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunPurple,
                modifier = Modifier.align(Alignment.End).clickable { onForgotPassword() }
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
            ) { Text("로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
            Row {
                Text("아직 RunQ 계정이 없나요? ", fontSize = 13.sp, color = RunGray)
                Text("가입하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunPurple, modifier = Modifier.clickable { onGoToSignUp() })
            }
            Spacer(Modifier.height(16.dp))
            Text("로그인 정보는 안전하게 암호화되어 저장됩니다.", fontSize = 11.sp, color = RunGray)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── 04 Auth / Password Recovery ──────────────────────
@Composable
fun AuthPasswordRecoveryScreen(onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(RunCream)) {
        AmbientGlows(lime = -120 to -90, lavender = 240 to 10, limeSize = 270, lavenderSize = 220)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(60.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AuthBackButton(onBack) }
            Spacer(Modifier.height(20.dp))
            Text("ACCOUNT RECOVERY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunPurple)
            Spacer(Modifier.height(10.dp))
            Text("가입하신 이메일을\n입력해주세요.", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RunBlack, lineHeight = 34.sp)
            Spacer(Modifier.height(10.dp))
            Text("비밀번호 재설정 링크를 보내드릴게요.", fontSize = 13.sp, color = RunGray)
            Spacer(Modifier.height(28.dp))
            Text("이메일", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunGray)
            Spacer(Modifier.height(8.dp))
            AuthField(email, { email = it }, "runq@email.com", Icons.Filled.Email, keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (email.isNotBlank()) sent = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
            ) { Text(if (sent) "재설정 메일 보냈어요 ✓" else "재설정 메일 받기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF3ECDD)).padding(16.dp)) {
                Column {
                    Text("메일이 오지 않나요?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Spacer(Modifier.height(6.dp))
                    Text("스팸함을 확인하거나 잠시 후 다시 시도해주세요.", fontSize = 12.sp, color = RunGray)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("RunQ는 계정 보안을 위해 재설정 링크를 이메일로만 보내요.", fontSize = 11.sp, color = RunGray)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── 05 Auth / Terms & Privacy ────────────────────────
private val termsSections = listOf(
    "제1조 (목적)" to "본 약관은 RunQ 서비스의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및 책임사항을 정하는 것을 목적으로 합니다.",
    "제2조 (이용계약)" to "이용계약은 이용자가 약관에 동의하고 회원가입을 완료한 시점부터 효력이 발생합니다. 서비스 이용 시 관계 법령과 운영정책을 준수해야 합니다.",
    "제3조 (서비스 이용)" to "RunQ는 러닝 코스, 기록, 위치 기반 장소 추천 등의 기능을 제공합니다. 일부 기능은 위치 권한 및 네트워크 연결이 필요할 수 있습니다.",
    "제4조 (개인정보)" to "회사는 서비스 제공을 위해 필요한 최소한의 개인정보만 수집하며, 관계 법령에 따라 안전하게 관리합니다."
)

@Composable
fun AuthTermsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(RunCream).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthBackButton(onBack)
            Spacer(Modifier.width(12.dp))
            Text("이용약관", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        }
        Spacer(Modifier.height(12.dp))
        Text("RunQ 서비스 이용을 위한 약관을 확인해주세요.", fontSize = 13.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))
        Text("TERMS & POLICY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RunPurple)
        Spacer(Modifier.height(8.dp))
        Text("RunQ 서비스 이용약관", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunBlack)
        Spacer(Modifier.height(6.dp))
        Text("최종 업데이트 · 2026. 09. 05", fontSize = 12.sp, color = RunGray)
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF3ECDD)).padding(16.dp)) {
            Column {
                Text("빠른 목차", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RunGray)
                Spacer(Modifier.height(8.dp))
                Text("01 목적   02 이용계약   03 서비스 이용   04 개인정보", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = RunBlack)
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(RunWhite).border(1.dp, RunBorderGray, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                termsSections.forEachIndexed { index, (title, body) ->
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunBlack)
                    Spacer(Modifier.height(8.dp))
                    Text(body, fontSize = 13.sp, color = RunBlack, lineHeight = 19.sp)
                    if (index != termsSections.lastIndex) Spacer(Modifier.height(20.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunLime, contentColor = RunBlack)
        ) { Text("동의하고 계속하기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(20.dp))
    }
}
