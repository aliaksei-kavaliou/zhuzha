package com.inkfirst.app.ui

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The five colours this app has, and the theme that stops Material 3 from
 * supplying any others.
 *
 * [Ink] on [Paper] is the whole palette, and it is a display constraint rather
 * than a taste: a reflective e-ink panel renders anything between them as a
 * dither pattern, which reads as noise on a page of text. [Muted] and
 * [Hairline] are the two exceptions, both long since validated on the Boox —
 * a mid grey for secondary labels and a lighter one for rules and borders,
 * where the dot pattern is either small enough or thin enough not to register.
 * [Scrim] dims a page under a modal.
 *
 * These five lived in seven copies before this file existed: `InkColor`,
 * `OverlayInkColor`, `ChatInkColor`, `NoteViewInkColor` and `DialogInk` were
 * all `Color(0xFF000000)` declared privately in whichever screen needed one
 * next.
 */
internal val Ink = Color(0xFF000000)
internal val Paper = Color(0xFFFFFFFF)
internal val Muted = Color(0xFF555555)
internal val Hairline = Color(0xFF888888)

/**
 * The recap block's wash. The ONE grey in the app, and it is on probation.
 *
 * E-ink has no grey: the panel thresholds and dithers, which is why a
 * 0.75dp grey dot vanished on the Boox and had to become a black one. A
 * large FILL is a different case — the Go 7 has 16 levels, so a light wash
 * can land on a real level instead of disappearing — but it is a different
 * case in THEORY, and the emulator renders it perfectly either way, so the
 * emulator cannot tell us. Verify on the panel before trusting it.
 *
 * Light enough to keep black text at full contrast, dark enough not to
 * quantise straight to paper. If it dithers into noise, the fallback is the
 * dashed marginal rule this replaced, which is known to work.
 */
internal val Wash = Color(0xFFE8E8E8)
internal val Scrim = Color(0x40000000)

/**
 * Every slot Material 3 will reach for, pointed at the two colours we have.
 *
 * The screens that ARE the book have styled themselves by hand from the start;
 * what this fixes is everything else — Library, Settings, New course, Backup,
 * and the stock components on all of them. Left to its defaults M3 supplies a
 * lilac-tinted purple, and on this panel purple is not a wrong accent but a
 * grey dot pattern: the "New course" button, the text-field focus ring, the
 * Switch, the text-selection handles in the reader itself.
 *
 * Two slots are easy to miss. [androidx.compose.material3.ColorScheme.surfaceTint]
 * defaults to `primary`, and M3 tints a surface by that colour as it "lifts" —
 * so with a black primary every elevated surface would come out grey; pointing
 * it at [Paper] makes elevation a no-op instead of a wash. And `error` is [Ink]
 * rather than a red, because a warning on a monochrome panel has to be a
 * warning in its WORDING; a red that arrives as speckled grey only looks like
 * damaged text.
 */
private val InkColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Paper,
    onPrimaryContainer = Ink,
    inversePrimary = Ink,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Paper,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    onTertiary = Paper,
    tertiaryContainer = Paper,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Muted,
    surfaceTint = Paper,
    // The surfaceContainer family is a SECOND set of surface colours, and
    // missing them is invisible until something uses one: menus take their
    // background from surfaceContainer, not from surface, so the dropdowns
    // stayed lilac after everything else had gone black and white. Found by
    // screenshotting a picker rather than by reading the scheme.
    surfaceBright = Paper,
    surfaceDim = Paper,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Paper,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    error = Ink,
    onError = Paper,
    errorContainer = Paper,
    onErrorContainer = Ink,
    outline = Hairline,
    outlineVariant = Hairline,
    scrim = Ink,
)

/**
 * Square, everywhere. [InkDialog] made this argument first and it holds for
 * every other stock component: a rounded borderless card has no edge at all
 * once the tint is gone, so it floats over the page with nothing separating it
 * from the text. Anything that interrupts a page should look like it came from
 * the same press as the page.
 */
// M3's Shapes only accepts corner-based shapes, so square is a zero radius
// rather than RectangleShape.
private val Square = RoundedCornerShape(0.dp)

private val InkShapes = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square,
)

/**
 * Selection is the one piece of chrome that lands ON the prose, so it gets
 * said explicitly rather than inherited: black handles, and a wash light
 * enough to read through. Selecting a passage is how the reader asks a
 * question, so this is not a rare state.
 */
private val InkSelection = TextSelectionColors(
    handleColor = Ink,
    backgroundColor = Ink.copy(alpha = 0.25f),
)

@Composable
internal fun InkFirstTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkColors, shapes = InkShapes) {
        CompositionLocalProvider(LocalTextSelectionColors provides InkSelection, content = content)
    }
}
