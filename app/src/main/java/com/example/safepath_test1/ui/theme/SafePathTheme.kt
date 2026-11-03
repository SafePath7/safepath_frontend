package com.example.safepath_test1.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SafeBlue = Color(0xFF17445E)
val SkyBlue = Color(0xFF63A9CC)
val SafeGreen = Color(0xFF16A36A)
val DestRed = Color(0xFF17445E)
val DangerRed = Color(0xFFDC2626)
val FieldBg = Color(0xFFEEF4F6)
val TextMuted = Color(0xFF647985)
val TextMain = Color(0xFF172D39)
val AppBackground = Color(0xFFF6F9FA)
val AppBorder = Color(0xFFDCE8EC)

private val LightColors =
    lightColorScheme(
        primary = SafeBlue,
        secondary = SkyBlue,
        background = AppBackground,
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSurface = TextMain,
        outline = AppBorder,
    )

private val SafePathTypography =
    Typography(
        headlineLarge =
            Typography()
                .headlineLarge
                .copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = Typography().bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )

private val SafePathShapes =
    Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
    )

@Composable
fun SafePathTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = SafePathTypography,
        shapes = SafePathShapes,
        content = content,
    )
}
