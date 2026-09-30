package app.notishade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notishade.R

// --- Colour -------------------------------------------------------------------------------------
//
// Near-monochrome by design: the UI is ink on paper, and colour is reserved for meaning.
// Only two hues ever appear — the indigo accent (the current selection, the primary action)
// and the red (blocked). Category identity is carried by icon shape and label, not by hue,
// so a screen of thirteen categories reads as one calm list instead of a colour chart.

private val Accent = Color(0xFF3B4A9E)
private val AccentLight = Color(0xFFB4BDEA)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E6F5),
    onPrimaryContainer = Color(0xFF212A5E),
    inversePrimary = AccentLight,
    secondary = Color(0xFF5B5B63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E7E9),
    onSecondaryContainer = Color(0xFF1B1B1F),
    tertiary = Color(0xFF4A4B52),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7E7E9),
    onTertiaryContainer = Color(0xFF1B1B1F),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF7DEDC),
    onErrorContainer = Color(0xFF8C1D18),
    background = Color(0xFFFAFAF9),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFAFAF9),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFE9E9E6),
    onSurfaceVariant = Color(0xFF5E5E63),
    surfaceTint = Accent,
    inverseSurface = Color(0xFF2E2E31),
    inverseOnSurface = Color(0xFFF4F4F2),
    outline = Color(0xFFA6A6A3),
    outlineVariant = Color(0xFFE2E2DE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE4E4E1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F3),
    surfaceContainer = Color(0xFFF1F1EE),
    surfaceContainerHigh = Color(0xFFEBEBE8),
    surfaceContainerHighest = Color(0xFFE6E6E2),
)

private val DarkColors = darkColorScheme(
    primary = AccentLight,
    onPrimary = Color(0xFF1B2457),
    primaryContainer = Color(0xFF2C3577),
    onPrimaryContainer = Color(0xFFDDE1F7),
    inversePrimary = Accent,
    secondary = Color(0xFFC5C5CB),
    onSecondary = Color(0xFF2D2D32),
    secondaryContainer = Color(0xFF3A3A40),
    onSecondaryContainer = Color(0xFFE4E4E9),
    tertiary = Color(0xFFC5C5CB),
    onTertiary = Color(0xFF2D2D32),
    tertiaryContainer = Color(0xFF3A3A40),
    onTertiaryContainer = Color(0xFFE4E4E9),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFEDEDF0),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFEDEDF0),
    surfaceVariant = Color(0xFF45454A),
    onSurfaceVariant = Color(0xFFA9A9AF),
    surfaceTint = AccentLight,
    inverseSurface = Color(0xFFEDEDF0),
    inverseOnSurface = Color(0xFF2A2A2E),
    outline = Color(0xFF6E6E74),
    outlineVariant = Color(0xFF2B2B2F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF34343A),
    surfaceDim = Color(0xFF0E0E10),
    surfaceContainerLowest = Color(0xFF090909),
    surfaceContainerLow = Color(0xFF141417),
    surfaceContainer = Color(0xFF18181B),
    surfaceContainerHigh = Color(0xFF202024),
    surfaceContainerHighest = Color(0xFF2A2A2E),
)

// --- Type ---------------------------------------------------------------------------------------

private fun inter(weight: FontWeight) = Font(
    R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Inter, subset to Latin and to the 300-700 weight range. Anything outside it falls back to the system font. */
val Inter = FontFamily(
    inter(FontWeight.Light),
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

// Headings are tracked tight and set in SemiBold rather than Bold; labels carry weight instead of
// size, which is what keeps dense lists legible without shouting.
val NotiShadeTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontSize = 52.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.6).sp, lineHeight = 58.sp),
    displayMedium = TextStyle(fontFamily = Inter, fontSize = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp, lineHeight = 48.sp),
    displaySmall = TextStyle(fontFamily = Inter, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.9).sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Inter, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.1).sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.5.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, lineHeight = 15.sp),
)

/** Lining figures of equal width, so counts don't jitter as they tick up. */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

/** Small caps-style section label: uppercase is applied by the caller, this carries the tracking. */
val OvertypeLabel = TextStyle(
    fontFamily = Inter,
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.9.sp,
    lineHeight = 16.sp,
)

// --- Shape --------------------------------------------------------------------------------------

// Softer than a hard-cornered UI, tighter than stock Material 3 - large surfaces stay calm
// instead of reading as pills.
val NotiShadeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun NotiShadeTheme(dark: Boolean, materialYou: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val colors = when {
        materialYou && dark -> dynamicDarkColorScheme(ctx)
        materialYou -> dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = NotiShadeTypography, shapes = NotiShadeShapes, content = content)
}

/** The launcher icon as a rounded tile. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.26f)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.mipmap.ic_launcher), "NotiShade", Modifier.fillMaxSize())
    }
}

@Composable
fun Wordmark(style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        "NotiShade",
        modifier,
        style = style.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
    )
}
