package com.smartclock.ui.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF004AC6)
val PrimaryDark = Color(0xFFB4C5FF)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF2563EB)
val OnPrimaryContainer = Color(0xFFEEEFFF)

val Secondary = Color(0xFF545F73)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFD5E0F8)
val OnSecondaryContainer = Color(0xFF586377)

val Tertiary = Color(0xFF46566C)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFF5E6E85)
val OnTertiaryContainer = Color(0xFFE9F0FF)

val BackgroundLight = Color(0xFFFAF8FF)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF3F3FE)
val SurfaceVariantLight = Color(0xFFE1E2ED)
val OutlineLight = Color(0xFFB9C0D5)
val OutlineVariantLight = Color(0xFFC9D0E2)
val OnSurfaceLight = Color(0xFF191B23)
val OnSurfaceVariantLight = Color(0xFF5B6274)
val InverseSurfaceLight = Color(0xFF2E3039)
val InverseOnSurfaceLight = Color(0xFFF0F0FB)
val InversePrimaryLight = Color(0xFFB4C5FF)
val SurfaceTintLight = Color(0xFF0053DB)

val BackgroundDark = Color(0xFF11131A)
val SurfaceDark = Color(0xFF171923)
val SurfaceVariantDark = Color(0xFF2A2D38)
val OutlineDark = Color(0xFF8E92A3)
val OutlineVariantDark = Color(0xFF454958)
val OnSurfaceDark = Color(0xFFF0F0FB)
val OnSurfaceVariantDark = Color(0xFFC4C7D5)
val InverseSurfaceDark = Color(0xFFF0F0FB)
val InverseOnSurfaceDark = Color(0xFF191B23)
val InversePrimaryDark = Color(0xFF004AC6)
val SurfaceTintDark = Color(0xFFB4C5FF)

val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)

val AlarmScreenBg = Color(0xFF0A0A0F)

fun labelColorHex(label: String?): String = when (label) {
    "工作" -> "#004AC6"
    "生活" -> "#2563EB"
    "学习" -> "#46566C"
    else -> "#737686"
}
