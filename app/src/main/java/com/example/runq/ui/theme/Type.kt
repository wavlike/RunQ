package com.example.runq.ui.theme

import androidx.compose.material3.Typography

// RunQ Foundations(Figma)가 "Noto Sans KR"을 전체 서체로 지정했으므로,
// Material3 기본 타이포그래피(크기/줄높이는 그대로 유지)의 fontFamily만 전부 교체한다.
private val base = Typography()
val Typography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = NotoSansKR),
    displayMedium = base.displayMedium.copy(fontFamily = NotoSansKR),
    displaySmall = base.displaySmall.copy(fontFamily = NotoSansKR),
    headlineLarge = base.headlineLarge.copy(fontFamily = NotoSansKR),
    headlineMedium = base.headlineMedium.copy(fontFamily = NotoSansKR),
    headlineSmall = base.headlineSmall.copy(fontFamily = NotoSansKR),
    titleLarge = base.titleLarge.copy(fontFamily = NotoSansKR),
    titleMedium = base.titleMedium.copy(fontFamily = NotoSansKR),
    titleSmall = base.titleSmall.copy(fontFamily = NotoSansKR),
    bodyLarge = base.bodyLarge.copy(fontFamily = NotoSansKR),
    bodyMedium = base.bodyMedium.copy(fontFamily = NotoSansKR),
    bodySmall = base.bodySmall.copy(fontFamily = NotoSansKR),
    labelLarge = base.labelLarge.copy(fontFamily = NotoSansKR),
    labelMedium = base.labelMedium.copy(fontFamily = NotoSansKR),
    labelSmall = base.labelSmall.copy(fontFamily = NotoSansKR)
)