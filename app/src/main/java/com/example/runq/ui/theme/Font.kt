package com.example.runq.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.example.runq.R

// RunQ Foundations(Figma "02_Foundations")가 지정한 서체: Noto Sans KR.
// 폰트 파일을 앱에 직접 번들하지 않고, Android가 기기에 이미 있는 Google Play Services
// Fonts Provider를 통해 런타임에 내려받는다(res/values/font_certs.xml의 인증서로 검증).
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val notoSansKRGoogleFont = GoogleFont("Noto Sans KR")

val NotoSansKR = FontFamily(
    Font(googleFont = notoSansKRGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = notoSansKRGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = notoSansKRGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = notoSansKRGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Black)
)
