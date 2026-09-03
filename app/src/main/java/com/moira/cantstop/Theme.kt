package com.moira.cantstop

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Every colour and line weight the board draws with, so light mode can be tuned for e-ink
 * without dark mode inheriting the compromises.
 */
data class BoardPalette(
    val playerColors: List<Color>,
    val spaceFill: Color,
    val spaceBorder: Color,
    val spaceBorderWidth: Dp,
    val topSpaceFill: Color,
    val topSpaceBorder: Color,
    val topSpaceBorderWidth: Dp,
    val claimedFillAlpha: Float,
    /** Claimed columns are told apart mainly by outline weight, not by their pale fill. */
    val claimedBorderWidth: Dp,
    val runnerFill: Color,
    val runnerBorder: Color,
    val runnerBorderWidth: Dp,
    val markerBorder: Color,
    val footerFill: Color,
    val footerBorder: Color,
)

private val PaperInk = Color(0xFF101010)

/**
 * Every grey in the light theme is an exact neutral — R, G and B identical — and must stay
 * that way. E-ink devices commonly ship with colour enhancement on, which multiplies
 * saturation; a "grey" like #6F757C carries a 13-point blue cast that the boost amplifies
 * into a visibly blue grid. #747474 has nothing to amplify.
 *
 * These were chosen to match the luminance of the tinted greys they replaced to within 0.002,
 * so every contrast figure quoted elsewhere in this file still holds.
 */
private val PaperGrey = Color(0xFF747474)      // grid lines,  L=0.175, 4.67:1 on white
private val PaperGreyDark = Color(0xFF646464)  // outlines,    L=0.127, 5.92:1 on white

/**
 * Text colour to sit on top of a solid player colour — picked per colour, not per palette.
 *
 * The light-mode cyan is deliberately paler than its three neighbours, so a single shared
 * value can't serve both: white on cyan is only 3.5:1 while black reaches 5.4:1, and the
 * reverse holds for the darker three. The threshold is 0.18 rather than the usual 0.5 because
 * every one of these fills is dark by design; it also nudges dark-mode red from 4.2:1 to 4.5:1.
 */
fun onPlayerColor(fill: Color): Color =
    if (fill.luminance() > 0.18f) PaperInk else Color(0xFFFFFFFF)

/**
 * Tuned for e-ink. White paper, near-black ink, no translucent greys — e-ink dithers those
 * into mush — and every element outlined so it still reads at low contrast. Player colours
 * are darkened well below their usual values: a Kaleido colour filter both desaturates and
 * dims, and these have to hold up against white.
 *
 * The four are spaced along a LIGHTNESS ladder, not just around the hue wheel. Hue is exactly
 * what a Kaleido filter weakens, so colours differing only in hue collapse together — an
 * earlier palette had all four at luminance 0.13-0.16, i.e. the same grey, and on the panel
 * the amber "yellow" was indistinguishable from red, and green from blue.
 *
 * The spacing is tuned to the cell layout rather than spread evenly. QUADRANT_ORDER in
 * Board.kt fixes which colours can share an EDGE of a cell (0-1, 1-2, 2-3, 3-0) and which
 * only ever touch at a corner (0-2 and 1-3), so the edge-adjacent pairs get the separation
 * and the diagonal pairs are allowed to run close:
 *
 *     edge-adjacent   red/green 1.95   green/blue 2.42   blue/cyan 3.41   red/cyan 2.74
 *     diagonal only   red/blue  1.24   green/cyan 1.41
 *
 * Those are pure-greyscale contrasts, so they hold with the colour stripped out entirely.
 */
val LightBoardPalette = BoardPalette(
    playerColors = listOf(
        Color(0xFFAE2621), // red        L=0.105
        Color(0xFF2A9E2E), // green      L=0.251
        Color(0xFF1449A0), // blue       L=0.075  darkest
        Color(0xFF00B5CD), // deep cyan  L=0.375  lightest
    ),
    spaceFill = Color(0xFFFFFFFF),
    spaceBorder = PaperGrey, // 4.67:1 on white — survives the e-ink front filter
    spaceBorderWidth = 1.dp,
    topSpaceFill = Color(0xFFFFFFFF),
    topSpaceBorder = PaperInk,
    topSpaceBorderWidth = 2.dp,
    // A pale tint over white is nearly invisible on e-ink, and four pale tints are
    // indistinguishable from each other, so lean on the outline and push the fill harder.
    claimedFillAlpha = 0.40f,
    claimedBorderWidth = 2.dp,
    runnerFill = Color(0xFFFFFFFF),
    runnerBorder = PaperInk,
    runnerBorderWidth = 2.5.dp,
    markerBorder = PaperInk,
    footerFill = Color(0xFFFFFFFF),
    footerBorder = PaperGrey,
)

private val ScreenInk = Color(0xFF121820)

/** For an ordinary backlit screen: same lightness ladder, shifted brighter for a dark field. */
val DarkBoardPalette = BoardPalette(
    playerColors = listOf(
        Color(0xFFDD504B), // red        L=0.216
        Color(0xFF34C339), // green      L=0.401
        Color(0xFF1E69E3), // blue       L=0.159  darkest
        Color(0xFF00D7F3), // deep cyan  L=0.551  lightest
    ),
    spaceFill = Color(0xFF1B2430),
    spaceBorder = Color(0xFF39465A),
    spaceBorderWidth = 1.dp,
    topSpaceFill = Color(0xFF2C2A1C),
    topSpaceBorder = Color(0xFFF2C230),
    topSpaceBorderWidth = 1.5.dp,
    claimedFillAlpha = 0.30f,
    claimedBorderWidth = 1.dp,
    runnerFill = Color(0xFFF7F9FC),
    runnerBorder = ScreenInk,
    runnerBorderWidth = 1.5.dp,
    markerBorder = Color(0xCC000000),
    footerFill = Color(0xFF243040),
    // The footer fill sits only 1.24:1 from a cell's fill, so its outline is what actually
    // separates the number row from the board. #39465A left that outline at 1.40:1 against
    // its own fill — effectively invisible. This is the same value as the scheme's `outline`,
    // and reaches 3.05:1. Deliberately stronger than the cell borders, since the footer is a
    // distinct element rather than one more space.
    footerBorder = Color(0xFF6C7A8C),
)

val LocalBoardPalette = staticCompositionLocalOf { LightBoardPalette }

/** The screen the fixed sizes below were originally chosen against. */
private val PhoneReferenceWidth = 380.dp
private val PhoneReferenceHeight = 760.dp

/**
 * How much wider than a phone this screen is, clamped to something sane.
 *
 * The board sizes itself from available width already, but fixed sizes — text, pill dots,
 * button heights — don't. On a 10.3 inch e-ink tablet the canvas is around 990 dp wide against
 * a phone's ~370, so phone-sized chrome would look like doll furniture next to an enormous
 * board. Multiply anything fixed by this.
 */
val LocalUiScale = staticCompositionLocalOf { 1f }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F3A5F),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = PaperInk,
    surface = Color(0xFFF3F3F3),
    onSurface = PaperInk,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF484848),
    outline = PaperGreyDark,
    error = Color(0xFFA8261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF2C230),
    onPrimary = ScreenInk,
    background = ScreenInk,
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF1B2430),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF243040),
    onSurfaceVariant = Color(0xFFB9C3D0),
    outline = Color(0xFF6C7A8C),
)

/**
 * Ripples animate, and animation on e-ink means ghosting and a full-panel flash. Suppressing
 * them app-wide costs nothing on an ordinary screen — taps still register, they just don't
 * splash.
 *
 * If this ever fails to compile against a newer Compose, delete this object and the
 * LocalRippleTheme line below; board taps stay ripple-free regardless, because the columns
 * pass indication = null directly.
 */
private object NoRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor(): Color = Color.Transparent

    @Composable
    override fun rippleAlpha(): RippleAlpha = RippleAlpha(0f, 0f, 0f, 0f)
}

@Composable
fun CantStopTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // System bar icons have to flip with the theme or they vanish into the background.
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !darkTheme
            bars.isAppearanceLightNavigationBars = !darkTheme
            // The window is what paints before Compose does, so it has to match too —
            // otherwise every cold start flashes the wrong colour, which on e-ink is a
            // full-panel refresh.
            window.setBackgroundDrawable(
                ColorDrawable((if (darkTheme) ScreenInk else Color.White).toArgb())
            )
        }
    }
    // Note the nesting: MaterialTheme provides its own LocalRippleTheme, so ours has to go
    // INSIDE it to win. Providing it outside compiles fine and silently does nothing.
    // Scale off whichever dimension is tighter. Width alone would over-scale on a 4:3 tablet:
    // the chrome grows, eats the board's vertical space, and the board — which sizes itself
    // from whichever axis is tighter — ends up SMALLER than if we hadn't scaled at all.
    val config = LocalConfiguration.current
    val uiScale = minOf(
        config.screenWidthDp.dp / PhoneReferenceWidth,
        config.screenHeightDp.dp / PhoneReferenceHeight,
    ).coerceIn(1f, 2.2f)

    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme) {
        CompositionLocalProvider(
            LocalBoardPalette provides if (darkTheme) DarkBoardPalette else LightBoardPalette,
            LocalUiScale provides uiScale,
            LocalRippleTheme provides NoRippleTheme,
            content = content,
        )
    }
}
